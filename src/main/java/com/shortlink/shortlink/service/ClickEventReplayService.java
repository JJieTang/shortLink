package com.shortlink.shortlink.service;

import com.shortlink.shortlink.exception.ResourceNotFoundException;
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
            @Value("${app.click-stream.stream-key}") String streamKey,
            @Value("${app.click-stream.dlq-stream-key}") String dlqStreamKey) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.streamKey = streamKey;
        this.dlqStreamKey = dlqStreamKey;
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
}
