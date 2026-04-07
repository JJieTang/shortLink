package com.shortlink.shortlink.service;

import com.shortlink.shortlink.event.ClickEventMessage;
import io.micrometer.core.instrument.Counter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class ClickEventStreamWorker {

    private static final Logger log = LoggerFactory.getLogger(ClickEventStreamWorker.class);
    private static final Duration PENDING_CLAIM_IDLE_TIME = Duration.ZERO;
    private static final Duration READ_FAILURE_BASE_BACKOFF = Duration.ofSeconds(1);
    private static final Duration READ_FAILURE_MAX_BACKOFF = Duration.ofSeconds(30);

    private final StringRedisTemplate stringRedisTemplate;
    private final ClickEventConsumer clickEventConsumer;
    private final ClickEventDlqHandler clickEventDlqHandler;
    private final Counter clickProcessingErrorsCounter;
    private final Executor clickEventExecutor;
    private final String streamKey;
    private final String consumerGroup;
    private final String consumerName;
    private final int maxRetries;
    private final int batchSize;
    private final Duration pollTimeout;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public ClickEventStreamWorker(
            StringRedisTemplate stringRedisTemplate,
            ClickEventConsumer clickEventConsumer,
            ClickEventDlqHandler clickEventDlqHandler,
            @Qualifier("clickProcessingErrorsCounter") Counter clickProcessingErrorsCounter,
            @Qualifier("clickEventWorkerExecutor") Executor clickEventExecutor,
            @Value("${app.click-stream.stream-key}") String streamKey,
            @Value("${app.click-stream.consumer-group}") String consumerGroup,
            @Value("${app.click-stream.consumer-name}") String consumerName,
            @Value("${app.click-stream.max-retries}") int maxRetries,
            @Value("${app.click-stream.batch-size}") int batchSize,
            @Value("${app.click-stream.poll-timeout}") Duration pollTimeout) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.clickEventConsumer = clickEventConsumer;
        this.clickEventDlqHandler = clickEventDlqHandler;
        this.clickProcessingErrorsCounter = clickProcessingErrorsCounter;
        this.clickEventExecutor = clickEventExecutor;
        this.streamKey = streamKey;
        this.consumerGroup = consumerGroup;
        this.consumerName = consumerName;
        this.maxRetries = maxRetries;
        this.batchSize = batchSize;
        this.pollTimeout = pollTimeout;
    }

    public void startPolling() {
        if (!running.compareAndSet(false, true)) {
            log.debug(
                    "Click-event polling worker for stream '{}' is already running for consumer '{}'",
                    streamKey,
                    consumerName
            );
            return;
        }

        clickEventExecutor.execute(this::pollLoop);
        log.info(
                "Started Redis stream polling worker for stream '{}' with consumer group '{}' and consumer '{}'",
                streamKey,
                consumerGroup,
                consumerName
        );
    }

    public void stopPolling() {
        if (running.compareAndSet(true, false)) {
            log.info(
                    "Stopping Redis stream polling worker for stream '{}' with consumer group '{}' and consumer '{}'",
                    streamKey,
                    consumerGroup,
                    consumerName
            );
        }
    }

    @PreDestroy
    void shutdown() {
        stopPolling();
    }

    public void processMessageBatch(List<MapRecord<String, Object, Object>> messages) {
        if (messages.isEmpty()) {
            return;
        }

        List<ClickEventMessage> eventMessages = messages.stream()
                .map(message -> toClickEventMessage(message.getValue()))
                .toList();

        clickEventConsumer.consumeBatch(eventMessages);
        acknowledgeBatch(messages);
    }

    void processPolledMessages(List<MapRecord<String, Object, Object>> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }

        try {
            processMessageBatch(messages);
        } catch (Exception exception) {
            fallbackToSingleMessageProcessing(messages, exception);
        }
    }

    void recoverPendingMessages() {
        while (running.get()) {
            PendingMessages pendingMessages = stringRedisTemplate.opsForStream().pending(
                    streamKey,
                    consumerGroup,
                    Range.unbounded(),
                    batchSize,
                    PENDING_CLAIM_IDLE_TIME
            );

            if (pendingMessages == null || pendingMessages.isEmpty()) {
                return;
            }

            RecordId[] pendingRecordIds = pendingMessages.stream()
                    .map(pendingMessage -> RecordId.of(pendingMessage.getIdAsString()))
                    .toArray(RecordId[]::new);
            List<MapRecord<String, Object, Object>> claimedMessages = stringRedisTemplate.opsForStream().claim(
                    streamKey,
                    consumerGroup,
                    consumerName,
                    PENDING_CLAIM_IDLE_TIME,
                    pendingRecordIds
            );

            if (claimedMessages == null || claimedMessages.isEmpty()) {
                log.debug(
                        "No pending click-event messages could be claimed for consumer '{}' in group '{}'",
                        consumerName,
                        consumerGroup
                );
                return;
            }

            log.info(
                    "Recovered {} pending click-event messages for consumer '{}' in group '{}'",
                    claimedMessages.size(),
                    consumerName,
                    consumerGroup
            );
            processPolledMessages(claimedMessages);
        }
    }

    private void pollLoop() {
        int consecutiveReadFailures = 0;

        try {
            if (running.get()) {
                try {
                    recoverPendingMessages();
                } catch (Exception exception) {
                    log.warn(
                            "Failed to recover pending click-event messages for consumer group '{}'",
                            consumerGroup,
                            exception
                    );
                }
            }

            while (running.get()) {
                try {
                    List<MapRecord<String, Object, Object>> messages = stringRedisTemplate.opsForStream().read(
                            Consumer.from(consumerGroup, consumerName),
                            StreamReadOptions.empty().count(batchSize).block(pollTimeout),
                            StreamOffset.create(streamKey, ReadOffset.lastConsumed())
                    );
                    consecutiveReadFailures = 0;

                    if (!running.get()) {
                        break;
                    }

                    if (messages == null || messages.isEmpty()) {
                        continue;
                    }

                    processPolledMessages(messages);
                } catch (Exception exception) {
                    if (!running.get()) {
                        log.debug(
                                "Click-event polling worker for stream '{}' stopped while waiting for more messages",
                                streamKey
                        );
                        break;
                    }

                    consecutiveReadFailures++;
                    Duration backoff = calculateReadFailureBackoff(consecutiveReadFailures);
                    log.warn(
                            "Failed to read click-event batch from stream '{}'. Backing off for {} ms before retrying.",
                            streamKey,
                            backoff.toMillis(),
                            exception
                    );
                    pauseAfterReadFailure(backoff);
                }
            }
        } finally {
            running.set(false);
            try {
                log.info(
                        "Stopped Redis stream polling worker for stream '{}' with consumer group '{}' and consumer '{}'",
                        streamKey,
                        consumerGroup,
                        consumerName
                );
            } catch (Exception exception) {
                log.debug("Failed to log polling-worker shutdown for stream '{}'", streamKey, exception);
            }
        }
    }

    Duration calculateReadFailureBackoff(int consecutiveReadFailures) {
        if (consecutiveReadFailures <= 0) {
            return Duration.ZERO;
        }

        Duration backoff = READ_FAILURE_BASE_BACKOFF;
        for (int i = 1; i < consecutiveReadFailures; i++) {
            backoff = backoff.multipliedBy(2);
            if (backoff.compareTo(READ_FAILURE_MAX_BACKOFF) >= 0) {
                return READ_FAILURE_MAX_BACKOFF;
            }
        }

        return backoff;
    }

    void pauseAfterReadFailure(Duration backoff) {
        if (!running.get() || backoff.isZero() || backoff.isNegative()) {
            return;
        }

        try {
            Thread.sleep(backoff.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            running.set(false);
            log.debug(
                    "Interrupted while backing off after a click-event stream read failure for stream '{}'",
                    streamKey,
                    exception
            );
        }
    }

    private void fallbackToSingleMessageProcessing(
            List<MapRecord<String, Object, Object>> messages,
            Exception batchException) {
        log.warn(
                "Batch processing failed for {} click-event messages from stream '{}'. Falling back to per-message handling.",
                messages.size(),
                streamKey,
                batchException
        );

        for (MapRecord<String, Object, Object> message : messages) {
            try {
                processMessageBatch(List.of(message));
            } catch (Exception messageException) {
                handleFailedMessage(message, messageException);
            }
        }
    }

    private void handleFailedMessage(
            MapRecord<String, Object, Object> message,
            Exception exception) {
        clickProcessingErrorsCounter.increment();
        int retryCount = readRetryCount(message.getValue());

        try {
            if (retryCount < maxRetries) {
                requeueWithIncrementedRetry(message, retryCount + 1);
                stringRedisTemplate.opsForStream().acknowledge(streamKey, consumerGroup, message.getId());
                log.warn(
                        "Retrying click event {} from stream '{}' ({}/{})",
                        message.getId(),
                        streamKey,
                        retryCount + 1,
                        maxRetries,
                        exception
                );
                return;
            }

            clickEventDlqHandler.moveToDlq(message, exception);
            stringRedisTemplate.opsForStream().acknowledge(streamKey, consumerGroup, message.getId());
            log.warn(
                    "Moved failed click event {} from stream '{}' to DLQ after {} retries",
                    message.getId(),
                    streamKey,
                    retryCount,
                    exception
            );
        } catch (Exception dlqException) {
            log.error(
                    "Failed to handle click event {} from stream '{}' after consumer error",
                    message.getId(),
                    streamKey,
                    dlqException
            );
        }
    }

    private void requeueWithIncrementedRetry(
            MapRecord<String, Object, Object> message,
            int nextRetryCount) {
        Map<String, String> payload = stringPayload(message.getValue());
        payload.put("retryCount", String.valueOf(nextRetryCount));
        payload.put("lastRetriedAt", Instant.now().toString());
        stringRedisTemplate.opsForStream().add(streamKey, payload);
    }

    private int readRetryCount(Map<Object, Object> payload) {
        String rawRetryCount = stringValue(payload.get("retryCount"));
        if (rawRetryCount == null || rawRetryCount.isBlank()) {
            return 0;
        }

        try {
            return Integer.parseInt(rawRetryCount);
        } catch (NumberFormatException exception) {
            log.warn("Invalid retryCount '{}' found in click-event payload, falling back to 0", rawRetryCount);
            return 0;
        }
    }

    private void acknowledgeBatch(List<MapRecord<String, Object, Object>> messages) {
        RecordId[] recordIds = messages.stream()
                .map(MapRecord::getId)
                .toArray(RecordId[]::new);
        stringRedisTemplate.opsForStream().acknowledge(streamKey, consumerGroup, recordIds);
    }

    private ClickEventMessage toClickEventMessage(Map<Object, Object> payload) {
        return new ClickEventMessage(
                UUID.fromString(String.valueOf(payload.get("eventId"))),
                UUID.fromString(String.valueOf(payload.get("urlId"))),
                stringValue(payload.get("shortCode")),
                Instant.parse(String.valueOf(payload.get("clickedAt"))),
                stringValue(payload.get("ipAddress")),
                stringValue(payload.get("referrer")),
                stringValue(payload.get("userAgent")),
                stringValue(payload.get("traceId"))
        );
    }

    private Map<String, String> stringPayload(Map<Object, Object> payload) {
        Map<String, String> values = new LinkedHashMap<>();
        for (Map.Entry<Object, Object> entry : payload.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            values.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
        }
        return values;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
