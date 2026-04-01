package com.shortlink.shortlink.service;

import com.shortlink.shortlink.exception.ResourceNotFoundException;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ClickEventReplayService {

    private final StringRedisTemplate stringRedisTemplate;
    private final String streamKey;
    private final String dlqStreamKey;

    public ClickEventReplayService(
            StringRedisTemplate stringRedisTemplate,
            MeterRegistry meterRegistry,
            @Value("${app.click-stream.stream-key}") String streamKey,
            @Value("${app.click-stream.dlq-stream-key}") String dlqStreamKey) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.streamKey = streamKey;
        this.dlqStreamKey = dlqStreamKey;
        Gauge.builder("shortlink_dlq_size", this, ClickEventReplayService::dlqSize)
                .description("Current number of click-event messages in the DLQ stream")
                .register(meterRegistry);
    }

    public void replay(MapRecord<String, Object, Object> dlqMessage) {
        Map<String, String> payload = toReplayPayload(dlqMessage);
        stringRedisTemplate.opsForStream().add(streamKey, payload);
        stringRedisTemplate.opsForStream().delete(dlqStreamKey, dlqMessage.getId());
    }

    public void replayByMessageId(String dlqMessageId) {
        List<MapRecord<String, Object, Object>> messages =
                stringRedisTemplate.opsForStream().range(dlqStreamKey, Range.closed(dlqMessageId, dlqMessageId));

        if (messages == null || messages.isEmpty()) {
            throw new ResourceNotFoundException("DLQ message not found: " + dlqMessageId);
        }

        replay(messages.getFirst());
    }

    public List<DlqMessageView> listDlqMessages() {
        List<MapRecord<String, Object, Object>> messages =
                stringRedisTemplate.opsForStream().range(dlqStreamKey, Range.unbounded());

        if (messages == null || messages.isEmpty()) {
            return List.of();
        }

        return messages.stream()
                .map(this::toDlqMessageView)
                .toList();
    }

    private Map<String, String> toReplayPayload(MapRecord<String, Object, Object> dlqMessage) {
        Map<String, String> payload = new LinkedHashMap<>();

        for (Map.Entry<Object, Object> entry : dlqMessage.getValue().entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }

            payload.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
        }

        payload.remove("originalMessageId");
        payload.remove("failedAt");
        payload.remove("errorType");
        payload.remove("errorMessage");
        payload.put("retryCount", "0");
        payload.put("replayedAt", Instant.now().toString());
        payload.put("replayedFromDlqMessageId", dlqMessage.getId().getValue());

        return payload;
    }

    private DlqMessageView toDlqMessageView(MapRecord<String, Object, Object> dlqMessage) {
        Map<Object, Object> payload = dlqMessage.getValue();

        return new DlqMessageView(
                dlqMessage.getId().getValue(),
                stringValue(payload.get("eventId")),
                stringValue(payload.get("urlId")),
                stringValue(payload.get("shortCode")),
                stringValue(payload.get("failedAt")),
                stringValue(payload.get("errorType")),
                stringValue(payload.get("errorMessage")),
                stringValue(payload.get("retryCount"))
        );
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private double dlqSize() {
        Long size = stringRedisTemplate.opsForStream().size(dlqStreamKey);
        return size == null ? 0.0 : size.doubleValue();
    }

    public record DlqMessageView(
            String messageId,
            String eventId,
            String urlId,
            String shortCode,
            String failedAt,
            String errorType,
            String errorMessage,
            String retryCount) {
    }
}
