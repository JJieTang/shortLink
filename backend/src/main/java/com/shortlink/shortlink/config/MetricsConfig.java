package com.shortlink.shortlink.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.PendingMessagesSummary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Configuration
public class MetricsConfig {

    private static final Logger log = LoggerFactory.getLogger(MetricsConfig.class);
    private static final StringRedisSerializer STRING_SERIALIZER = new StringRedisSerializer();

    @Bean
    public Counter redirectsCounter(MeterRegistry meterRegistry) {
        return Counter.builder("shortlink_redirects_total")
                .description("Total number of successful short-link redirects")
                .register(meterRegistry);
    }

    @Bean
    public Timer redirectLatencyTimer(MeterRegistry meterRegistry) {
        return Timer.builder("shortlink_redirect_latency_seconds")
                .description("Latency of successful short-link redirects")
                .serviceLevelObjectives(
                        Duration.ofMillis(1),
                        Duration.ofMillis(5),
                        Duration.ofMillis(10),
                        Duration.ofMillis(50),
                        Duration.ofMillis(100),
                        Duration.ofMillis(500)
                )
                .register(meterRegistry);
    }

    @Bean
    public Counter urlsCreatedCounter(MeterRegistry meterRegistry) {
        return Counter.builder("shortlink_urls_created_total")
                .description("Total number of successfully created short URLs")
                .register(meterRegistry);
    }

    @Bean
    public Counter cacheHitsCounter(MeterRegistry meterRegistry) {
        return Counter.builder("shortlink_cache_hits_total")
                .description("Redis URL cache hits")
                .register(meterRegistry);
    }

    @Bean
    public Counter cacheMissesCounter(MeterRegistry meterRegistry) {
        return Counter.builder("shortlink_cache_misses_total")
                .description("Redis URL cache misses")
                .register(meterRegistry);
    }

    @Bean
    public Counter droppedEventsCounter(MeterRegistry meterRegistry) {
        return Counter.builder("shortlink_click_events_dropped_total")
                .description("Number of click events dropped because they could not be published")
                .register(meterRegistry);
    }

    @Bean
    public Counter clickProcessingErrorsCounter(MeterRegistry meterRegistry) {
        return Counter.builder("shortlink_click_processing_errors_total")
                .description("Number of click-event processing failures before retry or DLQ handling")
                .register(meterRegistry);
    }

    @Bean
    public Gauge dlqSizeGauge(
            MeterRegistry meterRegistry,
            StringRedisTemplate stringRedisTemplate,
            @Value("${app.click-stream.dlq-stream-key}") String dlqStreamKey) {
        return Gauge.builder("shortlink_dlq_size", () -> readDlqSize(stringRedisTemplate, dlqStreamKey))
                .description("Current number of click-event messages in the DLQ stream")
                .register(meterRegistry);
    }

    @Bean
    public Gauge clickEventConsumerLagGauge(
            MeterRegistry meterRegistry,
            RedisConnectionFactory connectionFactory,
            @Value("${app.click-stream.stream-key}") String streamKey,
            @Value("${app.click-stream.consumer-group}") String consumerGroup) {
        return Gauge.builder("shortlink_consumer_lag", () -> readConsumerLag(connectionFactory, streamKey, consumerGroup))
                .description("Current number of pending click-event messages for the Redis Stream consumer group")
                .register(meterRegistry);
    }

    private double readDlqSize(StringRedisTemplate stringRedisTemplate, String dlqStreamKey) {
        try {
            Long size = stringRedisTemplate.opsForStream().size(dlqStreamKey);
            return size == null ? 0.0 : size.doubleValue();
        } catch (Exception exception) {
            log.debug("Failed to read DLQ size for stream '{}'", dlqStreamKey, exception);
            return 0.0;
        }
    }

    private double readConsumerLag(
            RedisConnectionFactory connectionFactory,
            String streamKey,
            String consumerGroup) {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            PendingMessagesSummary summary = connection.streamCommands().xPending(
                    STRING_SERIALIZER.serialize(streamKey),
                    consumerGroup
            );
            return summary == null ? 0.0 : summary.getTotalPendingMessages();
        } catch (Exception exception) {
            log.debug(
                    "Failed to read consumer lag for stream '{}' and group '{}'",
                    streamKey,
                    consumerGroup,
                    exception
            );
            return 0.0;
        }
    }
}
