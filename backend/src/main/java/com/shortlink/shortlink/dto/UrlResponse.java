package com.shortlink.shortlink.dto;


import com.shortlink.shortlink.model.Url;

import java.time.Instant;
import java.util.UUID;

public record UrlResponse (
        UUID id,
        String shortCode,
        String shortUrl,
        String originalUrl,
        Long totalClicks,
        Instant expiresAt,
        Instant createdAt,
        Instant updatedAt

) {
    public static UrlResponse from(Url url, String baseUrl) {
        return new UrlResponse(
                url.getId(),
                url.getShortCode(),
                buildShortUrl(baseUrl, url.getShortCode()),
                url.getOriginalUrl(),
                url.getTotalClicks(),
                url.getExpiresAt(),
                url.getCreatedAt(),
                url.getUpdatedAt()
        );
    }

    private static String buildShortUrl(String baseUrl, String shortCode) {
        if (baseUrl.endsWith("/")) {
            return baseUrl + shortCode;
        }
        return baseUrl + "/" + shortCode;
    }
}
