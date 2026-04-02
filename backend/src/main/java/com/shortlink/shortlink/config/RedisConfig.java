package com.shortlink.shortlink.config;

import com.shortlink.shortlink.service.ClickEventStreamWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    private static final Logger log = LoggerFactory.getLogger(RedisConfig.class);
    private static final StringRedisSerializer STRING_SERIALIZER = new StringRedisSerializer();

    @Bean
    public ApplicationRunner clickStreamInitializer(
            RedisConnectionFactory connectionFactory,
            ClickEventStreamWorker clickEventStreamWorker,
            @Value("${app.click-stream.stream-key}") String streamKey,
            @Value("${app.click-stream.consumer-group}") String consumerGroup) {
        return args -> {
            initializeConsumerGroup(connectionFactory, streamKey, consumerGroup);
            clickEventStreamWorker.startPolling();
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
}
