package com.shortlink.shortlink.service;

import com.shortlink.shortlink.event.ClickEventMessage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.connection.RedisStreamCommands;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClickEventPublisherTest {

    private StringRedisTemplate stringRedisTemplate;
    private StreamOperations<String, Object, Object> streamOperations;
    private SimpleMeterRegistry meterRegistry;
    private ClickEventPublisher clickEventPublisher;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        stringRedisTemplate = mock(StringRedisTemplate.class);
        streamOperations = mock(StreamOperations.class);
        meterRegistry = new SimpleMeterRegistry();
        Counter droppedEventsCounter = meterRegistry.counter("shortlink_click_events_dropped_total");

        when(stringRedisTemplate.opsForStream()).thenReturn(streamOperations);

        clickEventPublisher = new ClickEventPublisher(
                stringRedisTemplate,
                droppedEventsCounter,
                "click-events",
                100_000
        );
    }

    @Test
    void shouldPublishClickEventToRedisStream() {
        ClickEventMessage eventMessage = sampleEventMessage();
        when(streamOperations.add(any(), any(RedisStreamCommands.XAddOptions.class)))
                .thenReturn(mock(RecordId.class));

        clickEventPublisher.publish(eventMessage);

        ArgumentCaptor<RedisStreamCommands.XAddOptions> optionsCaptor =
                ArgumentCaptor.forClass(RedisStreamCommands.XAddOptions.class);
        verify(streamOperations).add(any(), optionsCaptor.capture());
        RedisStreamCommands.XAddOptions xAddOptions = optionsCaptor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals(100_000L, xAddOptions.getMaxlen());
        org.junit.jupiter.api.Assertions.assertTrue(xAddOptions.isApproximateTrimming());
        assertEquals(0.0, droppedEventsCounter().count());
    }

    @Test
    void shouldIncrementDroppedMetricWhenPublishFails() {
        ClickEventMessage eventMessage = sampleEventMessage();
        when(streamOperations.add(any(), any(RedisStreamCommands.XAddOptions.class)))
                .thenThrow(new RuntimeException("Redis unavailable"));

        assertDoesNotThrow(() -> clickEventPublisher.publish(eventMessage));
        assertEquals(1.0, droppedEventsCounter().count());
    }

    private ClickEventMessage sampleEventMessage() {
        return new ClickEventMessage(
                UUID.fromString("550e8400-e29b-41d4-a716-446655440000"),
                UUID.fromString("550e8400-e29b-41d4-a716-446655440001"),
                "abc1234",
                Instant.parse("2026-03-31T12:00:00Z"),
                "127.0.0.1",
                "https://example.com/ref",
                "Mozilla/5.0",
                "trace-123"
        );
    }

    private Counter droppedEventsCounter() {
        return meterRegistry.get("shortlink_click_events_dropped_total").counter();
    }
}
