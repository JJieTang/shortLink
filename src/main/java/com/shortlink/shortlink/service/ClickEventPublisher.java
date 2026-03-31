package com.shortlink.shortlink.service;

import com.shortlink.shortlink.event.ClickEventMessage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class ClickEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ClickEventPublisher.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final String streamKey;
    private final Counter droppedEventsCounter;

    public ClickEventPublisher(
            StringRedisTemplate stringRedisTemplate,
            MeterRegistry meterRegistry,
            @Value("${app.click-stream.stream-key}") String streamKey) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.streamKey = streamKey;
        this.droppedEventsCounter = Counter.builder("shortlink_click_events_dropped_total")
                .description("Number of click events dropped because they could not be published")
                .register(meterRegistry);
    }

    public void publish(ClickEventMessage eventMessage) {
        try {
            RecordId recordId = stringRedisTemplate.opsForStream().add(
                    StreamRecords.mapBacked(toPayload(eventMessage)).withStreamKey(streamKey)
            );

            log.debug(
                    "Published click event {} for shortCode {} to stream {} with record id {}",
                    eventMessage.eventId(),
                    eventMessage.shortCode(),
                    streamKey,
                    recordId
            );
        } catch (Exception exception) {
            droppedEventsCounter.increment();
            log.warn(
                    "Failed to publish click event {} for shortCode {}. Redirect flow will continue without analytics.",
                    eventMessage.eventId(),
                    eventMessage.shortCode(),
                    exception
            );
        }
    }

    private Map<String, String> toPayload(ClickEventMessage eventMessage) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("eventId", eventMessage.eventId().toString());
        payload.put("urlId", eventMessage.urlId().toString());
        payload.put("shortCode", eventMessage.shortCode());
        payload.put("clickedAt", eventMessage.clickedAt().toString());
        putIfNotBlank(payload, "ipAddress", eventMessage.ipAddress());
        putIfNotBlank(payload, "referrer", eventMessage.referrer());
        putIfNotBlank(payload, "userAgent", eventMessage.userAgent());
        putIfNotBlank(payload, "traceId", eventMessage.traceId());
        return payload;
    }

    private void putIfNotBlank(Map<String, String> payload, String key, String value) {
        if (value != null && !value.isBlank()) {
            payload.put(key, value);
        }
    }
}
