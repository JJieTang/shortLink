package com.shortlink.shortlink.service;

import com.shortlink.shortlink.config.ShortlinkMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RateLimitServiceTest {

    private StringRedisTemplate stringRedisTemplate;
    private SimpleMeterRegistry meterRegistry;
    private RateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        stringRedisTemplate = mock(StringRedisTemplate.class);
        meterRegistry = new SimpleMeterRegistry();
        Clock fixedClock = Clock.fixed(Instant.parse("2026-04-02T12:00:00Z"), ZoneOffset.UTC);

        rateLimitService = new RateLimitService(
                stringRedisTemplate,
                meterRegistry,
                "rate-limit",
                fixedClock
        );
    }

    @Test
    void shouldAllowRequestWithinCapacity() {
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any(), any(), any()))
                .thenReturn(List.of(1L, 4L, 0L));

        RateLimitService.RateLimitDecision decision = rateLimitService.checkRateLimit(
                "public",
                "ip:127.0.0.1",
                new RateLimitService.RateLimitPolicy(5, Duration.ofSeconds(10))
        );

        assertTrue(decision.allowed());
        assertEquals(4L, decision.remainingTokens());
        assertEquals(0L, decision.retryAfterSeconds());
        assertEquals(0.0, rateLimitedCount("public"));
    }

    @Test
    void shouldRejectRequestWhenBucketIsEmpty() {
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any(), any(), any()))
                .thenReturn(List.of(0L, 0L, 2L));

        RateLimitService.RateLimitDecision decision = rateLimitService.checkRateLimit(
                "admin",
                "user:550e8400-e29b-41d4-a716-446655440000",
                new RateLimitService.RateLimitPolicy(2, Duration.ofSeconds(30))
        );

        assertFalse(decision.allowed());
        assertEquals(0L, decision.remainingTokens());
        assertEquals(2L, decision.retryAfterSeconds());
        assertEquals(1.0, rateLimitedCount("admin"));
    }

    @Test
    void shouldPassExpectedKeyAndArgumentsToRedisScript() {
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any(), any(), any()))
                .thenReturn(List.of(1L, 9L, 0L));

        rateLimitService.checkRateLimit(
                "public",
                "ip:203.0.113.10",
                new RateLimitService.RateLimitPolicy(10, Duration.ofMinutes(1))
        );

        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> capacityCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> refillPeriodCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> nowCaptor = ArgumentCaptor.forClass(String.class);

        verify(stringRedisTemplate).execute(
                any(RedisScript.class),
                keysCaptor.capture(),
                capacityCaptor.capture(),
                refillPeriodCaptor.capture(),
                nowCaptor.capture()
        );

        assertEquals(List.of("rate-limit:public:ip:203.0.113.10"), keysCaptor.getValue());
        assertEquals("10", capacityCaptor.getValue());
        assertEquals("60000", refillPeriodCaptor.getValue());
        assertEquals("1775131200000", nowCaptor.getValue());
    }

    @Test
    void shouldAllowRequestWhenRedisIsUnavailable() {
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any(), any(), any()))
                .thenThrow(new RuntimeException("Redis unavailable"));

        RateLimitService.RateLimitDecision decision = rateLimitService.checkRateLimit(
                "public",
                "ip:203.0.113.11",
                new RateLimitService.RateLimitPolicy(8, Duration.ofSeconds(20))
        );

        assertTrue(decision.allowed());
        assertEquals(8L, decision.remainingTokens());
        assertEquals(0L, decision.retryAfterSeconds());
        assertEquals(0.0, rateLimitedCount("public"));
    }

    private double rateLimitedCount(String scope) {
        io.micrometer.core.instrument.Counter counter = meterRegistry.find(ShortlinkMetrics.RATE_LIMITED_TOTAL)
                .tag(ShortlinkMetrics.SCOPE_TAG, scope)
                .counter();
        return counter == null ? 0.0 : counter.count();
    }
}
