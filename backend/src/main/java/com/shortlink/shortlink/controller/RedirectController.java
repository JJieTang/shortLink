package com.shortlink.shortlink.controller;

import com.shortlink.shortlink.config.ShortlinkMetrics;
import com.shortlink.shortlink.event.ClickEventMessage;
import com.shortlink.shortlink.exception.BaseException;
import com.shortlink.shortlink.service.ClickEventPublisher;
import com.shortlink.shortlink.service.RedirectService;
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

    public RedirectController(
            RedirectService redirectService,
            ClickEventPublisher clickEventPublisher
    ) {
        this.redirectService = redirectService;
        this.clickEventPublisher = clickEventPublisher;
    }

    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode, HttpServletRequest request) {
        RedirectService.RedirectTarget redirectTarget;

        try {
            redirectTarget = redirectService.resolveRedirectTarget(shortCode);
        } catch (BaseException exception) {
            request.setAttribute(ShortlinkMetrics.REDIRECT_CACHE_RESULT_REQUEST_ATTRIBUTE, ShortlinkMetrics.CACHE_UNKNOWN);
            throw exception;
        } catch (RuntimeException exception) {
            request.setAttribute(ShortlinkMetrics.REDIRECT_CACHE_RESULT_REQUEST_ATTRIBUTE, ShortlinkMetrics.CACHE_UNKNOWN);
            throw exception;
        }

        String cacheResult = redirectTarget.cacheHit() ? ShortlinkMetrics.CACHE_HIT : ShortlinkMetrics.CACHE_MISS;
        request.setAttribute(ShortlinkMetrics.REDIRECT_CACHE_RESULT_REQUEST_ATTRIBUTE, cacheResult);

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
