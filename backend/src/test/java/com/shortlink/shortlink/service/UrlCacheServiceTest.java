package com.shortlink.shortlink.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UrlCacheServiceTest {

    private StringRedisTemplate stringRedisTemplate;
    private HashOperations<String, Object, Object> hashOperations;
    private SimpleMeterRegistry meterRegistry;
    private UrlCacheService urlCacheService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        stringRedisTemplate = mock(StringRedisTemplate.class);
        hashOperations = mock(HashOperations.class);
        meterRegistry = new SimpleMeterRegistry();
        Counter cacheHitsCounter = meterRegistry.counter("shortlink_cache_hits_total");
        Counter cacheMissesCounter = meterRegistry.counter("shortlink_cache_misses_total");

        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);

        urlCacheService = new UrlCacheService(
                stringRedisTemplate,
                cacheHitsCounter,
                cacheMissesCounter,
                Duration.ofHours(24)
        );
    }

    @Test
    void shouldIncrementHitCounterWhenCacheEntryIsUsable() {
        Map<Object, Object> entries = new LinkedHashMap<>();
        entries.put("urlId", "550e8400-e29b-41d4-a716-446655440000");
        entries.put("originalUrl", "https://example.com/cached");
        entries.put("expiresAt", "2026-04-01T10:00:00Z");
        when(hashOperations.entries("url:abc1234")).thenReturn(entries);

        UrlCacheService.CachedUrl cachedUrl = urlCacheService.findByShortCode("abc1234").orElseThrow();

        assertEquals(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"), cachedUrl.urlId());
        assertEquals("https://example.com/cached", cachedUrl.originalUrl());
        assertEquals(Instant.parse("2026-04-01T10:00:00Z"), cachedUrl.expiresAt());
        assertEquals(1.0, cacheHitsCounter().count());
        assertEquals(0.0, cacheMissesCounter().count());
    }

    @Test
    void shouldIncrementMissCounterWhenCacheEntryDoesNotExist() {
        when(hashOperations.entries("url:missing")).thenReturn(Map.of());

        assertTrue(urlCacheService.findByShortCode("missing").isEmpty());
        assertEquals(0.0, cacheHitsCounter().count());
        assertEquals(1.0, cacheMissesCounter().count());
    }

    @Test
    void shouldIncrementMissCounterWhenCacheEntryIsMalformed() {
        Map<Object, Object> entries = new LinkedHashMap<>();
        entries.put("urlId", "550e8400-e29b-41d4-a716-446655440000");
        when(hashOperations.entries("url:broken")).thenReturn(entries);

        assertTrue(urlCacheService.findByShortCode("broken").isEmpty());
        assertEquals(0.0, cacheHitsCounter().count());
        assertEquals(1.0, cacheMissesCounter().count());
    }

    @Test
    void shouldReturnNullExpirationWhenCacheEntryHasNoExpiryField() {
        Map<Object, Object> entries = new LinkedHashMap<>();
        entries.put("urlId", "550e8400-e29b-41d4-a716-446655440000");
        entries.put("originalUrl", "https://example.com/no-expiry");
        when(hashOperations.entries("url:no-expiry")).thenReturn(entries);

        UrlCacheService.CachedUrl cachedUrl = urlCacheService.findByShortCode("no-expiry").orElseThrow();

        assertNull(cachedUrl.expiresAt());
        assertEquals(1.0, cacheHitsCounter().count());
        assertEquals(0.0, cacheMissesCounter().count());
    }

    private Counter cacheHitsCounter() {
        return meterRegistry.get("shortlink_cache_hits_total").counter();
    }

    private Counter cacheMissesCounter() {
        return meterRegistry.get("shortlink_cache_misses_total").counter();
    }
}
