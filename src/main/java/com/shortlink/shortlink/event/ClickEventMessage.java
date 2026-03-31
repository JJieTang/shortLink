package com.shortlink.shortlink.event;

import java.time.Instant;
import java.util.UUID;

public record ClickEventMessage(
        UUID eventId,
        UUID urlId,
        String shortCode,
        Instant clickedAt,
        String ipAddress,
        String referrer,
        String userAgent,
        String traceId
) {
}
