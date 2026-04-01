package com.shortlink.shortlink.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class ClickEventDlqHandler {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 500;

    private final StringRedisTemplate stringRedisTemplate;
    private final String dlqStreamKey;

    public ClickEventDlqHandler(
            StringRedisTemplate stringRedisTemplate,
            @Value("${app.click-stream.dlq-stream-key}") String dlqStreamKey) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.dlqStreamKey = dlqStreamKey;
    }

    public void moveToDlq(MapRecord<String, Object, Object> message, Exception exception) {
        Map<String, String> payload = new LinkedHashMap<>();
        for (Map.Entry<Object, Object> entry : message.getValue().entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            payload.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
        }
        payload.put("originalMessageId", message.getId().getValue());
        payload.put("failedAt", Instant.now().toString());
        payload.put("errorType", exception.getClass().getSimpleName());
        payload.put("errorMessage", truncate(exception.getMessage()));

        stringRedisTemplate.opsForStream().add(dlqStreamKey, payload);
    }

    private String truncate(String message) {
        if (message == null || message.isBlank()) {
            return "No error message available";
        }

        return message.length() <= MAX_ERROR_MESSAGE_LENGTH
                ? message
                : message.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }
}
