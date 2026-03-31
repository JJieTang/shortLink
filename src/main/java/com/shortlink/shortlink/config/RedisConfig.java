package com.shortlink.shortlink.config;

import com.shortlink.shortlink.event.ClickEventMessage;
import com.shortlink.shortlink.service.ClickEventConsumer;
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
import java.util.Map;
import java.util.UUID;

@Configuration
public class RedisConfig {

    private static final Logger log = LoggerFactory.getLogger(RedisConfig.class);

    @Bean
    public StreamMessageListenerContainer<String, MapRecord<String, String, String>> clickEventListenerContainer(
            RedisConnectionFactory connectionFactory,
            @Value("${app.click-stream.batch-size}") int batchSize,
            @Value("${app.click-stream.poll-timeout}") Duration pollTimeout) {
        StringRedisSerializer serializer = new StringRedisSerializer();
        StreamMessageListenerContainer.StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options =
                StreamMessageListenerContainer.StreamMessageListenerContainerOptions.<String, MapRecord<String, String, String>>builder()
                        .batchSize(batchSize)
                        .pollTimeout(pollTimeout)
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
            @Value("${app.click-stream.stream-key}") String streamKey,
            @Value("${app.click-stream.consumer-group}") String consumerGroup,
            @Value("${app.click-stream.consumer-name}") String consumerName) {
        return args -> {
            initializeConsumerGroup(connectionFactory, streamKey, consumerGroup);
            registerAndStartListener(
                    stringRedisTemplate,
                    clickEventListenerContainer,
                    clickEventConsumer,
                    streamKey,
                    consumerGroup,
                    consumerName
            );
        };
    }

    private void initializeConsumerGroup(
            RedisConnectionFactory connectionFactory,
            String streamKey,
            String consumerGroup) {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            StringRedisSerializer serializer = new StringRedisSerializer();

            connection.streamCommands().xGroupCreate(
                    serializer.serialize(streamKey),
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
            String streamKey,
            String consumerGroup,
            String consumerName) {
        try {
            listenerContainer.receive(
                    Consumer.from(consumerGroup, consumerName),
                    StreamOffset.create(streamKey, ReadOffset.lastConsumed()),
                    message -> {
                        try {
                            clickEventConsumer.consume(toClickEventMessage(message.getValue()));
                            stringRedisTemplate.opsForStream().acknowledge(streamKey, consumerGroup, message.getId());
                        } catch (Exception exception) {
                            log.warn("Failed to consume click event {} from stream '{}'", message.getId(), streamKey, exception);
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
