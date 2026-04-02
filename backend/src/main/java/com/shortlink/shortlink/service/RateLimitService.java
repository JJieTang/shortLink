package com.shortlink.shortlink.service;

import io.micrometer.core.instrument.Counter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

@Service
public class RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);

    private static final RedisScript<List> RATE_LIMIT_SCRIPT = createRateLimitScript();

    private final StringRedisTemplate stringRedisTemplate;
    private final Counter rateLimitedCounter;
    private final String keyPrefix;
    private final Clock clock;

    public RateLimitService(
            StringRedisTemplate stringRedisTemplate,
            @Qualifier("rateLimitedCounter") Counter rateLimitedCounter,
            @Value("${app.rate-limit.key-prefix}") String keyPrefix) {
        this(stringRedisTemplate, rateLimitedCounter, keyPrefix, Clock.systemUTC());
    }

    RateLimitService(
            StringRedisTemplate stringRedisTemplate,
            Counter rateLimitedCounter,
            String keyPrefix,
            Clock clock) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.rateLimitedCounter = rateLimitedCounter;
        this.keyPrefix = keyPrefix;
        this.clock = clock;
    }

    public RateLimitDecision checkRateLimit(String scope, String subject, RateLimitPolicy policy) {
        validateScope(scope);
        validateSubject(subject);
        Objects.requireNonNull(policy, "policy must not be null");

        String redisKey = buildKey(scope, subject);
        long nowMillis = clock.millis();

        try {
            List<?> result = stringRedisTemplate.execute(
                    RATE_LIMIT_SCRIPT,
                    List.of(redisKey),
                    Long.toString(policy.capacity()),
                    Long.toString(policy.refillPeriod().toMillis()),
                    Long.toString(nowMillis)
            );

            RateLimitDecision decision = toDecision(result);
            if (!decision.allowed()) {
                rateLimitedCounter.increment();
            }
            return decision;
        } catch (Exception exception) {
            log.warn(
                    "Failed to evaluate rate limit for scope '{}' and subject '{}'. Allowing request as degraded behavior.",
                    scope,
                    subject,
                    exception
            );
            return new RateLimitDecision(true, policy.capacity(), 0);
        }
    }

    private RateLimitDecision toDecision(List<?> result) {
        if (result == null || result.size() < 3) {
            throw new IllegalStateException("Redis rate-limit script returned an invalid response");
        }

        boolean allowed = toLong(result.get(0)) == 1L;
        long remainingTokens = toLong(result.get(1));
        long retryAfterSeconds = toLong(result.get(2));
        return new RateLimitDecision(allowed, remainingTokens, retryAfterSeconds);
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw new IllegalStateException("Redis rate-limit script returned a non-numeric response");
    }

    private String buildKey(String scope, String subject) {
        return keyPrefix + ":" + scope + ":" + subject;
    }

    private void validateScope(String scope) {
        if (scope == null || scope.isBlank()) {
            throw new IllegalArgumentException("scope must not be blank");
        }
    }

    private void validateSubject(String subject) {
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("subject must not be blank");
        }
    }

    private static RedisScript<List> createRateLimitScript() {
        DefaultRedisScript<List> redisScript = new DefaultRedisScript<>();
        redisScript.setResultType(List.class);
        redisScript.setScriptText("""
                local key = KEYS[1]
                local capacity = tonumber(ARGV[1])
                local refill_period_ms = tonumber(ARGV[2])
                local now_ms = tonumber(ARGV[3])

                local state = redis.call('HMGET', key, 'tokens', 'last_refill_ms')
                local tokens = tonumber(state[1])
                local last_refill_ms = tonumber(state[2])

                if tokens == nil then
                    tokens = capacity
                end

                if last_refill_ms == nil then
                    last_refill_ms = now_ms
                end

                local refill_rate = capacity / refill_period_ms
                local elapsed_ms = math.max(0, now_ms - last_refill_ms)
                local refilled_tokens = math.min(capacity, tokens + (elapsed_ms * refill_rate))

                local allowed = 0
                local remaining_tokens = refilled_tokens
                local retry_after_seconds = 0

                if refilled_tokens >= 1 then
                    allowed = 1
                    remaining_tokens = refilled_tokens - 1
                else
                    local missing_tokens = 1 - refilled_tokens
                    retry_after_seconds = math.ceil(missing_tokens / refill_rate / 1000)
                end

                redis.call('HSET', key,
                    'tokens', remaining_tokens,
                    'last_refill_ms', now_ms
                )

                local ttl_ms = math.max(refill_period_ms, 1000)
                redis.call('PEXPIRE', key, ttl_ms)

                return {allowed, math.floor(remaining_tokens), retry_after_seconds}
                """);
        return redisScript;
    }

    public record RateLimitDecision(boolean allowed, long remainingTokens, long retryAfterSeconds) {
    }

    public record RateLimitPolicy(long capacity, Duration refillPeriod) {

        public RateLimitPolicy {
            if (capacity <= 0) {
                throw new IllegalArgumentException("capacity must be greater than zero");
            }
            Objects.requireNonNull(refillPeriod, "refillPeriod must not be null");
            if (refillPeriod.isZero() || refillPeriod.isNegative()) {
                throw new IllegalArgumentException("refillPeriod must be greater than zero");
            }
        }
    }
}
