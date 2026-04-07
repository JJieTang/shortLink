package com.shortlink.shortlink.config;

import com.shortlink.shortlink.exception.RateLimitedException;
import com.shortlink.shortlink.exception.UnauthorizedException;
import com.shortlink.shortlink.security.CurrentUserService;
import com.shortlink.shortlink.service.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.util.UUID;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final String PUBLIC_SCOPE = "public";
    private static final String MANAGEMENT_SCOPE = "management";

    private final RateLimitService rateLimitService;
    private final CurrentUserService currentUserService;
    private final RateLimitService.RateLimitPolicy publicPolicy;
    private final RateLimitService.RateLimitPolicy managementPolicy;

    public RateLimitInterceptor(
            RateLimitService rateLimitService,
            CurrentUserService currentUserService,
            @Value("${app.rate-limit.public.capacity}") long publicCapacity,
            @Value("${app.rate-limit.public.refill-period}") Duration publicRefillPeriod,
            @Value("${app.rate-limit.management.capacity}") long managementCapacity,
            @Value("${app.rate-limit.management.refill-period}") Duration managementRefillPeriod) {
        this.rateLimitService = rateLimitService;
        this.currentUserService = currentUserService;
        this.publicPolicy = new RateLimitService.RateLimitPolicy(publicCapacity, publicRefillPeriod);
        this.managementPolicy = new RateLimitService.RateLimitPolicy(managementCapacity, managementRefillPeriod);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String requestUri = request.getRequestURI();
        RateLimitService.RateLimitDecision decision;

        if (isManagementRequest(requestUri)) {
            UUID userId = resolveCurrentUserId();
            if (userId == null) {
                return true;
            }

            decision = rateLimitService.checkRateLimit(
                    MANAGEMENT_SCOPE,
                    "user:" + userId,
                    managementPolicy
            );
        } else {
            decision = rateLimitService.checkRateLimit(
                    PUBLIC_SCOPE,
                    "ip:" + resolveClientIp(request),
                    publicPolicy
            );
        }

        if (!decision.allowed()) {
            throw new RateLimitedException("Rate limit exceeded. Please retry later.", decision.retryAfterSeconds());
        }

        return true;
    }

    private UUID resolveCurrentUserId() {
        try {
            return currentUserService.getCurrentUserId();
        } catch (UnauthorizedException exception) {
            return null;
        }
    }

    private boolean isManagementRequest(String requestUri) {
        return requestUri.startsWith("/api/v1/urls") || requestUri.startsWith("/api/v1/admin");
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
