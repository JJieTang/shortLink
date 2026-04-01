package com.shortlink.shortlink.config;

import com.shortlink.shortlink.event.ClickEventMessage;
import com.shortlink.shortlink.service.ClickEventConsumer;
import com.shortlink.shortlink.service.ClickEventDlqHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;

@Configuration
public class RedisConfig {

    private static final Logger log = LoggerFactory.getLogger(RedisConfig.class);
    private static final StringRedisSerializer STRING_SERIALIZER = new StringRedisSerializer();

    @Bean
    public StreamMessageListenerContainer<String, MapRecord<String, String, String>> clickEventListenerContainer(
            RedisConnectionFactory connectionFactory,
            Executor clickEventExecutor,
            @Value("${app.click-stream.batch-size}") int batchSize,
            @Value("${app.click-stream.poll-timeout}") Duration pollTimeout) {
        StringRedisSerializer serializer = new StringRedisSerializer();
        StreamMessageListenerContainer.StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options =
                StreamMessageListenerContainer.StreamMessageListenerContainerOptions.<String, MapRecord<String, String, String>>builder()
                        .batchSize(batchSize)
                        .pollTimeout(pollTimeout)
                        .executor(clickEventExecutor)
                        .serializer(serializer)
                        .autoStartup(false)
                        .build();

        return StreamMessageListenerContainer.create(connectionFactory, options);
    }

    @Bean
    public ApplicationRunner clickStreamInitializer(
            RedisConnectionFactory connectionFactory,
            StringRedisTemplate stringRedisTemplate,
            StreamMessageListenerContainer<String, MapRecord<String, String, String>> clickEventListenerContainer,
            ClickEventConsumer clickEventConsumer,
            ClickEventDlqHandler clickEventDlqHandler,
            @Value("${app.click-stream.stream-key}") String streamKey,
            @Value("${app.click-stream.consumer-group}") String consumerGroup,
            @Value("${app.click-stream.consumer-name}") String consumerName,
            @Value("${app.click-stream.max-retries}") int maxRetries) {
        return args -> {
            initializeConsumerGroup(connectionFactory, streamKey, consumerGroup);
            registerAndStartListener(
                    stringRedisTemplate,
                    clickEventListenerContainer,
                    clickEventConsumer,
                    clickEventDlqHandler,
                    streamKey,
                    consumerGroup,
                    consumerName,
                    maxRetries
            );
        };
    }

    private void initializeConsumerGroup(
            RedisConnectionFactory connectionFactory,
            String streamKey,
            String consumerGroup) {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            connection.streamCommands().xGroupCreate(
                    STRING_SERIALIZER.serialize(streamKey),
                    consumerGroup,
                    ReadOffset.latest(),
                    true
            );

            log.info("Initialized Redis stream consumer group '{}' for stream '{}'", consumerGroup, streamKey);
        } catch (Exception exception) {
            if (isBusyGroupError(exception)) {
                log.info("Redis stream consumer group '{}' already exists for stream '{}'", consumerGroup, streamKey);
                return;
            }

            log.warn(
                    "Skipping Redis stream consumer group initialization for stream '{}' because Redis is unavailable or not ready",
                    streamKey,
                    exception
            );
        }
    }

    private void registerAndStartListener(
            StringRedisTemplate stringRedisTemplate,
            StreamMessageListenerContainer<String, MapRecord<String, String, String>> listenerContainer,
            ClickEventConsumer clickEventConsumer,
            ClickEventDlqHandler clickEventDlqHandler,
            String streamKey,
            String consumerGroup,
            String consumerName,
            int maxRetries) {
        try {
            listenerContainer.receive(
                    Consumer.from(consumerGroup, consumerName),
                    StreamOffset.create(streamKey, ReadOffset.lastConsumed()),
                    message -> {
                        try {
                            processMessageBatch(
                                    stringRedisTemplate,
                                    clickEventConsumer,
                                    streamKey,
                                    consumerGroup,
                                    List.of(message)
                            );
                        } catch (Exception exception) {
                            handleFailedMessage(
                                    stringRedisTemplate,
                                    clickEventDlqHandler,
                                    streamKey,
                                    consumerGroup,
                                    message,
                                    exception,
                                    maxRetries
                            );
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

    private void processMessageBatch(
            StringRedisTemplate stringRedisTemplate,
            ClickEventConsumer clickEventConsumer,
            String streamKey,
            String consumerGroup,
            List<MapRecord<String, String, String>> messages) {
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
            StringRedisTemplate stringRedisTemplate,
            ClickEventDlqHandler clickEventDlqHandler,
            String streamKey,
            String consumerGroup,
            MapRecord<String, String, String> message,
            Exception exception,
            int maxRetries) {
        int retryCount = readRetryCount(message.getValue());

        try {
            if (retryCount < maxRetries) {
                requeueWithIncrementedRetry(stringRedisTemplate, streamKey, message, retryCount + 1);
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
            StringRedisTemplate stringRedisTemplate,
            String streamKey,
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

    private boolean isBusyGroupError(Throwable throwable) {
        Throwable current = throwable;

        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains("BUSYGROUP")) {
                return true;
            }
            current = current.getCause();
        }

        return false;
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
