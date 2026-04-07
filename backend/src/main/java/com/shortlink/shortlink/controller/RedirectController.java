package com.shortlink.shortlink.controller;

import com.shortlink.shortlink.config.ShortlinkMetrics;
import com.shortlink.shortlink.event.ClickEventMessage;
import com.shortlink.shortlink.exception.BaseException;
import com.shortlink.shortlink.service.ClickEventPublisher;
import com.shortlink.shortlink.service.RedirectService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

@RestController
public class RedirectController {

    private final RedirectService redirectService;
    private final ClickEventPublisher clickEventPublisher;
    private final MeterRegistry meterRegistry;

    public RedirectController(
            RedirectService redirectService,
            ClickEventPublisher clickEventPublisher,
            MeterRegistry meterRegistry
    ) {
        this.redirectService = redirectService;
        this.clickEventPublisher = clickEventPublisher;
        this.meterRegistry = meterRegistry;
    }

    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode, HttpServletRequest request) {
        Timer.Sample sample = Timer.start(meterRegistry);
        RedirectService.RedirectTarget redirectTarget;

        try {
            redirectTarget = redirectService.resolveRedirectTarget(shortCode);
        } catch (BaseException exception) {
            recordRedirectMetrics(Integer.toString(exception.getStatus()), ShortlinkMetrics.CACHE_UNKNOWN, sample);
            throw exception;
        } catch (RuntimeException exception) {
            recordRedirectMetrics(Integer.toString(HttpStatus.INTERNAL_SERVER_ERROR.value()), ShortlinkMetrics.CACHE_UNKNOWN, sample);
            throw exception;
        }

        String cacheResult = redirectTarget.cacheHit() ? ShortlinkMetrics.CACHE_HIT : ShortlinkMetrics.CACHE_MISS;
        recordRedirectMetrics(Integer.toString(HttpStatus.FOUND.value()), cacheResult, sample);

        clickEventPublisher.publish(new ClickEventMessage(
                UUID.randomUUID(),
                redirectTarget.urlId(),
                redirectTarget.shortCode(),
                Instant.now(),
                resolveClientIp(request),
                request.getHeader(HttpHeaders.REFERER),
                request.getHeader(HttpHeaders.USER_AGENT),
                resolveTraceId(request)
        ));

        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, URI.create(redirectTarget.originalUrl()).toString())
                .build();
    }

    private void recordRedirectMetrics(String status, String cacheResult, Timer.Sample sample) {
        sample.stop(Timer.builder(ShortlinkMetrics.REDIRECT_LATENCY)
                .description("Latency of short-link redirect requests")
                .tags(
                        ShortlinkMetrics.STATUS_TAG, status,
                        ShortlinkMetrics.CACHE_RESULT_TAG, cacheResult
                )
                .register(meterRegistry));

        meterRegistry.counter(
                ShortlinkMetrics.REDIRECTS_TOTAL,
                ShortlinkMetrics.STATUS_TAG, status,
                ShortlinkMetrics.CACHE_RESULT_TAG, cacheResult
        ).increment();
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String resolveTraceId(HttpServletRequest request) {
        String requestId = request.getHeader("X-Request-Id");
        if (requestId != null && !requestId.isBlank()) {
            return requestId;
        }
        return UUID.randomUUID().toString();
    }
}
