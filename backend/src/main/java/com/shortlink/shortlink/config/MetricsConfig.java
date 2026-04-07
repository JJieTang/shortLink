package com.shortlink.shortlink.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
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
    public MeterFilter redirectLatencyDistributionConfig() {
        DistributionStatisticConfig distributionConfig = DistributionStatisticConfig.builder()
                .serviceLevelObjectives(
                        Duration.ofMillis(1).toNanos(),
                        Duration.ofMillis(5).toNanos(),
                        Duration.ofMillis(10).toNanos(),
                        Duration.ofMillis(50).toNanos(),
                        Duration.ofMillis(100).toNanos(),
                        Duration.ofMillis(500).toNanos()
                )
                .build();

        return new MeterFilter() {
            @Override
            public DistributionStatisticConfig configure(
                    io.micrometer.core.instrument.Meter.Id id,
                    DistributionStatisticConfig config) {
                if (ShortlinkMetrics.REDIRECT_LATENCY.equals(id.getName())) {
                    return distributionConfig.merge(config);
                }
                return config;
            }
        };
    }

    @Bean
    public Counter cacheHitsCounter(MeterRegistry meterRegistry) {
        return Counter.builder(ShortlinkMetrics.CACHE_HITS_TOTAL)
                .description("Redis URL cache hits")
                .register(meterRegistry);
    }

    @Bean
    public Counter cacheMissesCounter(MeterRegistry meterRegistry) {
        return Counter.builder(ShortlinkMetrics.CACHE_MISSES_TOTAL)
                .description("Redis URL cache misses")
                .register(meterRegistry);
    }

    @Bean
    public Counter clickProcessingErrorsCounter(MeterRegistry meterRegistry) {
        return Counter.builder(ShortlinkMetrics.CLICK_PROCESSING_ERRORS_TOTAL)
                .description("Number of click-event processing failures before retry or DLQ handling")
                .register(meterRegistry);
    }

    @Bean
    public Gauge dlqSizeGauge(
            MeterRegistry meterRegistry,
            StringRedisTemplate stringRedisTemplate,
            @Value("${app.click-stream.dlq-stream-key}") String dlqStreamKey) {
        return Gauge.builder(ShortlinkMetrics.DLQ_SIZE, () -> readDlqSize(stringRedisTemplate, dlqStreamKey))
                .description("Current number of click-event messages in the DLQ stream")
                .register(meterRegistry);
    }

    @Bean
    public Gauge clickEventConsumerLagGauge(
            MeterRegistry meterRegistry,
            RedisConnectionFactory connectionFactory,
            @Value("${app.click-stream.stream-key}") String streamKey,
            @Value("${app.click-stream.consumer-group}") String consumerGroup) {
        return Gauge.builder(ShortlinkMetrics.CONSUMER_LAG, () -> readConsumerLag(connectionFactory, streamKey, consumerGroup))
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
