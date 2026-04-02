package com.shortlink.shortlink.dto;

import java.time.LocalDate;
import java.util.List;

public record AnalyticsResponse(
        String shortCode,
        long totalClicks,
        long periodClicks,
        long uniqueClicks,
        List<DailyClicks> clicksByDate
) {
    public record DailyClicks(
            LocalDate date,
            long clicks
    ) {
    }
}
