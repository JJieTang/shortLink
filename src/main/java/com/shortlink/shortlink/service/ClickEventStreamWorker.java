package com.shortlink.shortlink.service;

import com.shortlink.shortlink.event.ClickEventMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ClickEventStreamWorker {

    private static final Logger log = LoggerFactory.getLogger(ClickEventStreamWorker.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final StreamMessageListenerContainer<String, MapRecord<String, String, String>> listenerContainer;
    private final ClickEventConsumer clickEventConsumer;
    private final ClickEventDlqHandler clickEventDlqHandler;
    private final String streamKey;
    private final String consumerGroup;
    private final String consumerName;
    private final int maxRetries;

    public ClickEventStreamWorker(
            StringRedisTemplate stringRedisTemplate,
            StreamMessageListenerContainer<String, MapRecord<String, String, String>> listenerContainer,
            ClickEventConsumer clickEventConsumer,
            ClickEventDlqHandler clickEventDlqHandler,
            @Value("${app.click-stream.stream-key}") String streamKey,
            @Value("${app.click-stream.consumer-group}") String consumerGroup,
            @Value("${app.click-stream.consumer-name}") String consumerName,
            @Value("${app.click-stream.max-retries}") int maxRetries) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.listenerContainer = listenerContainer;
        this.clickEventConsumer = clickEventConsumer;
        this.clickEventDlqHandler = clickEventDlqHandler;
        this.streamKey = streamKey;
        this.consumerGroup = consumerGroup;
        this.consumerName = consumerName;
        this.maxRetries = maxRetries;
    }

    public void startListener() {
        try {
            listenerContainer.receive(
                    Consumer.from(consumerGroup, consumerName),
                    StreamOffset.create(streamKey, ReadOffset.lastConsumed()),
                    message -> {
                        try {
                            processMessageBatch(List.of(message));
                        } catch (Exception exception) {
                            handleFailedMessage(message, exception);
                        }
                    }
            );
            listenerContainer.start();
            log.info(
                    "Started Redis stream listener for stream '{}' with consumer group '{}' and consumer '{}'",
                    streamKey,
                    consumerGroup,
                    consumerName
            );
        } catch (Exception exception) {
            log.warn("Skipping Redis stream listener startup for stream '{}'", streamKey, exception);
        }
    }

    public void processMessageBatch(List<MapRecord<String, String, String>> messages) {
        if (messages.isEmpty()) {
            return;
        }

        List<ClickEventMessage> eventMessages = messages.stream()
                .map(message -> toClickEventMessage(message.getValue()))
                .toList();

        clickEventConsumer.consumeBatch(eventMessages);
        for (MapRecord<String, String, String> message : messages) {
            stringRedisTemplate.opsForStream().acknowledge(streamKey, consumerGroup, message.getId());
        }
    }

    private void handleFailedMessage(
            MapRecord<String, String, String> message,
            Exception exception) {
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
            MapRecord<String, String, String> message,
            int nextRetryCount) {
        Map<String, String> payload = new LinkedHashMap<>(message.getValue());
        payload.put("retryCount", String.valueOf(nextRetryCount));
        payload.put("lastRetriedAt", Instant.now().toString());
        stringRedisTemplate.opsForStream().add(streamKey, payload);
    }

    private int readRetryCount(Map<String, String> payload) {
        String rawRetryCount = payload.get("retryCount");
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

    private ClickEventMessage toClickEventMessage(Map<String, String> payload) {
        return new ClickEventMessage(
                UUID.fromString(payload.get("eventId")),
                UUID.fromString(payload.get("urlId")),
                payload.get("shortCode"),
                Instant.parse(payload.get("clickedAt")),
                payload.get("ipAddress"),
                payload.get("referrer"),
                payload.get("userAgent"),
                payload.get("traceId")
        );
    }
}
