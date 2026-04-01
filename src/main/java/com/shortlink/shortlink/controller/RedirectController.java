package com.shortlink.shortlink.controller;

import com.shortlink.shortlink.event.ClickEventMessage;
import com.shortlink.shortlink.service.ClickEventPublisher;
import com.shortlink.shortlink.service.RedirectService;
import io.micrometer.core.instrument.Counter;
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
    private final Counter redirectsCounter;
    private final Timer redirectLatencyTimer;

    public RedirectController(
            RedirectService redirectService,
            ClickEventPublisher clickEventPublisher,
            MeterRegistry meterRegistry
    ) {
        this.redirectService = redirectService;
        this.clickEventPublisher = clickEventPublisher;
        this.redirectsCounter = Counter.builder("shortlink_redirects_total")
                .description("Total number of successful short-link redirects")
                .register(meterRegistry);
        this.redirectLatencyTimer = Timer.builder("shortlink_redirect_latency_seconds")
                .description("Latency of successful short-link redirects")
                .register(meterRegistry);
    }

    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode, HttpServletRequest request) {
        Timer.Sample sample = Timer.start();
        RedirectService.RedirectTarget redirectTarget = redirectService.resolveRedirectTarget(shortCode);

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
        redirectsCounter.increment();
        sample.stop(redirectLatencyTimer);

        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, URI.create(redirectTarget.originalUrl()).toString())
                .build();
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
