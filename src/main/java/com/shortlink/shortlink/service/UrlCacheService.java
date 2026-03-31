package com.shortlink.shortlink.service;

import com.shortlink.shortlink.model.Url;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class UrlCacheService {

    private static final String KEY_PREFIX = "url:";
    private static final String URL_ID_FIELD = "urlId";
    private static final String ORIGINAL_URL_FIELD = "originalUrl";
    private static final String EXPIRES_AT_FIELD = "expiresAt";

    private final StringRedisTemplate stringRedisTemplate;
    private final Duration urlTtl;

    public UrlCacheService(
            StringRedisTemplate stringRedisTemplate,
            @Value("${app.cache.url-ttl}") Duration urlTtl) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.urlTtl = urlTtl;
    }

    public Optional<CachedUrl> findByShortCode(String shortCode) {
        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(buildKey(shortCode));
        if (entries.isEmpty()) {
            return Optional.empty();
        }

        Object urlId = entries.get(URL_ID_FIELD);
        Object originalUrl = entries.get(ORIGINAL_URL_FIELD);
        if (!(urlId instanceof String urlIdValue) || urlIdValue.isBlank()) {
            return Optional.empty();
        }

        if (!(originalUrl instanceof String originalUrlValue) || originalUrlValue.isBlank()) {
            return Optional.empty();
        }

        Object expiresAt = entries.get(EXPIRES_AT_FIELD);
        return Optional.of(new CachedUrl(
                UUID.fromString(urlIdValue),
                originalUrlValue,
                expiresAt instanceof String expiresAtValue && !expiresAtValue.isBlank()
                        ? Instant.parse(expiresAtValue)
                        : null
        ));
    }

    public void cacheUrl(Url url) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(URL_ID_FIELD, url.getId().toString());
        values.put(ORIGINAL_URL_FIELD, url.getOriginalUrl());

        if (url.getExpiresAt() != null) {
            values.put(EXPIRES_AT_FIELD, url.getExpiresAt().toString());
        }

        String key = buildKey(url.getShortCode());
        stringRedisTemplate.opsForHash().putAll(key, values);
        stringRedisTemplate.expire(key, urlTtl);
    }

    public void evict(String shortCode) {
        stringRedisTemplate.delete(buildKey(shortCode));
    }

    private String buildKey(String shortCode) {
        return KEY_PREFIX + shortCode;
    }

    public record CachedUrl(UUID urlId, String originalUrl, Instant expiresAt) {
    }
}
