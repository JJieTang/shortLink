package com.shortlink.shortlink.service;

import com.shortlink.shortlink.event.ClickEventMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.Invocation;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClickEventStreamWorkerTest {

    private StringRedisTemplate stringRedisTemplate;
    private StreamOperations<String, Object, Object> streamOperations;
    private ClickEventConsumer clickEventConsumer;
    private ClickEventDlqHandler clickEventDlqHandler;
    private Executor clickEventExecutor;
    private ClickEventStreamWorker clickEventStreamWorker;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        stringRedisTemplate = mock(StringRedisTemplate.class);
        streamOperations = mock(StreamOperations.class);
        clickEventConsumer = mock(ClickEventConsumer.class);
        clickEventDlqHandler = mock(ClickEventDlqHandler.class);
        clickEventExecutor = mock(Executor.class);

        when(stringRedisTemplate.opsForStream()).thenReturn((StreamOperations) streamOperations);

        clickEventStreamWorker = new ClickEventStreamWorker(
                stringRedisTemplate,
                clickEventConsumer,
                clickEventDlqHandler,
                clickEventExecutor,
                "click-events",
                "click-event-consumers",
                "consumer-a",
                0,
                50,
                Duration.ofSeconds(2)
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldFallbackToPerMessageHandlingWhenBatchProcessingFails() {
        ClickEventMessage goodEvent = eventMessage(
                UUID.fromString("550e8400-e29b-41d4-a716-446655440001"),
                UUID.fromString("550e8400-e29b-41d4-a716-446655440101"),
                "good-link",
                "203.0.113.10"
        );
        ClickEventMessage badEvent = eventMessage(
                UUID.fromString("550e8400-e29b-41d4-a716-446655440002"),
                UUID.fromString("550e8400-e29b-41d4-a716-446655440102"),
                "bad-link",
                "203.0.113.11"
        );

        MapRecord<String, Object, Object> goodRecord = mock(MapRecord.class);
        RecordId goodRecordId = RecordId.of("1743500000000-0");
        when(goodRecord.getId()).thenReturn(goodRecordId);
        when(goodRecord.getValue()).thenReturn(payloadFor(goodEvent));

        MapRecord<String, Object, Object> badRecord = mock(MapRecord.class);
        RecordId badRecordId = RecordId.of("1743500000001-0");
        when(badRecord.getId()).thenReturn(badRecordId);
        when(badRecord.getValue()).thenReturn(payloadFor(badEvent));

        doAnswer(invocation -> {
            List<ClickEventMessage> eventMessages = invocation.getArgument(0);
            if (eventMessages.size() == 2) {
                throw new IllegalStateException("Batch failed");
            }

            ClickEventMessage eventMessage = eventMessages.getFirst();
            if (eventMessage.eventId().equals(badEvent.eventId())) {
                throw new IllegalStateException("Bad message");
            }

            return null;
        }).when(clickEventConsumer).consumeBatch(any());

        clickEventStreamWorker.processPolledMessages(List.of(goodRecord, badRecord));

        verify(clickEventConsumer, times(3)).consumeBatch(any());
        verify(clickEventConsumer).consumeBatch(argThat(eventMessages ->
                eventMessages.size() == 2
                        && eventMessages.get(0).eventId().equals(goodEvent.eventId())
                        && eventMessages.get(1).eventId().equals(badEvent.eventId())
        ));
        verify(clickEventConsumer).consumeBatch(argThat(eventMessages ->
                eventMessages.size() == 1
                        && eventMessages.getFirst().eventId().equals(goodEvent.eventId())
        ));
        verify(clickEventConsumer).consumeBatch(argThat(eventMessages ->
                eventMessages.size() == 1
                        && eventMessages.getFirst().eventId().equals(badEvent.eventId())
        ));

        List<List<RecordId>> acknowledgements = org.mockito.Mockito.mockingDetails(streamOperations)
                .getInvocations()
                .stream()
                .filter(invocation -> invocation.getMethod().getName().equals("acknowledge"))
                .map(this::toAcknowledgedRecordIds)
                .collect(Collectors.toList());
        assertEquals(2, acknowledgements.size());
        assertEquals(List.of(goodRecordId), acknowledgements.get(0));
        assertEquals(List.of(badRecordId), acknowledgements.get(1));

        verify(clickEventDlqHandler).moveToDlq(eq(badRecord), any(IllegalStateException.class));
        verify(clickEventDlqHandler, never()).moveToDlq(eq(goodRecord), any());
        assertEquals(0, acknowledgements.stream().filter(recordIds -> recordIds.size() == 2).count());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldRecoverPendingMessagesBeforeReadingNewOnes() {
        ClickEventMessage pendingEvent = eventMessage(
                UUID.fromString("550e8400-e29b-41d4-a716-446655440010"),
                UUID.fromString("550e8400-e29b-41d4-a716-446655440110"),
                "pending-link",
                "203.0.113.20"
        );

        MapRecord<String, Object, Object> pendingRecord = mock(MapRecord.class);
        RecordId pendingRecordId = RecordId.of("1743500000010-0");
        when(pendingRecord.getId()).thenReturn(pendingRecordId);
        when(pendingRecord.getValue()).thenReturn(payloadFor(pendingEvent));

        PendingMessages firstPendingBatch = new PendingMessages(
                "click-event-consumers",
                List.of(new PendingMessage(
                        pendingRecordId,
                        Consumer.from("click-event-consumers", "stale-consumer"),
                        Duration.ofSeconds(30),
                        1
                ))
        );
        PendingMessages emptyPendingBatch = new PendingMessages("click-event-consumers", List.of());

        when(streamOperations.pending(
                eq("click-events"),
                eq("click-event-consumers"),
                eq(Range.unbounded()),
                eq(50L),
                eq(Duration.ZERO)
        )).thenReturn(firstPendingBatch, emptyPendingBatch);
        when(streamOperations.claim(
                eq("click-events"),
                eq("click-event-consumers"),
                eq("consumer-a"),
                eq(Duration.ZERO),
                any(RecordId[].class)
        )).thenReturn(List.of(pendingRecord));

        clickEventStreamWorker.startPolling();
        clickEventStreamWorker.recoverPendingMessages();
        clickEventStreamWorker.stopPolling();

        verify(streamOperations, times(2)).pending(
                "click-events",
                "click-event-consumers",
                Range.unbounded(),
                50L,
                Duration.ZERO
        );
        verify(streamOperations).claim(
                eq("click-events"),
                eq("click-event-consumers"),
                eq("consumer-a"),
                eq(Duration.ZERO),
                any(RecordId[].class)
        );
        verify(clickEventConsumer).consumeBatch(argThat(eventMessages ->
                eventMessages.size() == 1
                        && eventMessages.getFirst().eventId().equals(pendingEvent.eventId())
        ));

        List<List<RecordId>> acknowledgements = org.mockito.Mockito.mockingDetails(streamOperations)
                .getInvocations()
                .stream()
                .filter(invocation -> invocation.getMethod().getName().equals("acknowledge"))
                .map(this::toAcknowledgedRecordIds)
                .collect(Collectors.toList());
        assertEquals(List.of(List.of(pendingRecordId)), acknowledgements);
        verify(clickEventDlqHandler, never()).moveToDlq(any(), any());
    }

    @Test
    void shouldUseExponentialBackoffForConsecutiveReadFailures() {
        assertEquals(Duration.ZERO, clickEventStreamWorker.calculateReadFailureBackoff(0));
        assertEquals(Duration.ofSeconds(1), clickEventStreamWorker.calculateReadFailureBackoff(1));
        assertEquals(Duration.ofSeconds(2), clickEventStreamWorker.calculateReadFailureBackoff(2));
        assertEquals(Duration.ofSeconds(4), clickEventStreamWorker.calculateReadFailureBackoff(3));
        assertEquals(Duration.ofSeconds(8), clickEventStreamWorker.calculateReadFailureBackoff(4));
        assertEquals(Duration.ofSeconds(16), clickEventStreamWorker.calculateReadFailureBackoff(5));
        assertEquals(Duration.ofSeconds(30), clickEventStreamWorker.calculateReadFailureBackoff(6));
        assertEquals(Duration.ofSeconds(30), clickEventStreamWorker.calculateReadFailureBackoff(7));
    }

    private ClickEventMessage eventMessage(UUID eventId, UUID urlId, String shortCode, String ipAddress) {
        return new ClickEventMessage(
                eventId,
                urlId,
                shortCode,
                Instant.parse("2026-03-31T12:00:00Z"),
                ipAddress,
                "https://example.com/ref",
                "Mozilla/5.0",
                "trace-123"
        );
    }

    private Map<Object, Object> payloadFor(ClickEventMessage eventMessage) {
        return Map.of(
                "eventId", eventMessage.eventId().toString(),
                "urlId", eventMessage.urlId().toString(),
                "shortCode", eventMessage.shortCode(),
                "clickedAt", eventMessage.clickedAt().toString(),
                "ipAddress", eventMessage.ipAddress(),
                "referrer", eventMessage.referrer(),
                "userAgent", eventMessage.userAgent(),
                "traceId", eventMessage.traceId()
        );
    }

    private List<RecordId> toAcknowledgedRecordIds(Invocation invocation) {
        Object thirdArgument = invocation.getArguments()[2];
        if (thirdArgument instanceof RecordId[] recordIds) {
            return List.of(recordIds);
        }

        return List.of((RecordId) thirdArgument);
    }
}
