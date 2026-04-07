package com.shortlink.shortlink.config;

import com.shortlink.shortlink.exception.UnauthorizedException;
import com.shortlink.shortlink.security.CurrentUserService;
import com.shortlink.shortlink.service.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RateLimitInterceptorTest {

    private RateLimitService rateLimitService;
    private CurrentUserService currentUserService;
    private RateLimitInterceptor interceptor;
    private HttpServletResponse response;

    @BeforeEach
    void setUp() {
        rateLimitService = mock(RateLimitService.class);
        currentUserService = mock(CurrentUserService.class);
        interceptor = new RateLimitInterceptor(
                rateLimitService,
                currentUserService,
                120,
                Duration.ofMinutes(1),
                300,
                Duration.ofMinutes(1)
        );
        response = mock(HttpServletResponse.class);
    }

    @Test
    void shouldSkipManagementRateLimitWhenRequestIsUnauthenticated() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/v1/urls");
        when(currentUserService.getCurrentUserId())
                .thenThrow(new UnauthorizedException("Authentication is required"));

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertTrue(allowed);
        verify(currentUserService).getCurrentUserId();
        verifyNoInteractions(rateLimitService);
    }

    @Test
    void shouldApplyManagementRateLimitWhenUserIsAuthenticated() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        UUID userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440099");
        when(request.getRequestURI()).thenReturn("/api/v1/urls");
        when(currentUserService.getCurrentUserId()).thenReturn(userId);
        when(rateLimitService.checkRateLimit(any(), any(), any()))
                .thenReturn(new RateLimitService.RateLimitDecision(true, 10, 0));

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertTrue(allowed);
        verify(rateLimitService).checkRateLimit(eq("management"), eq("user:" + userId), any());
    }

    @Test
    void shouldApplyPublicRateLimitToAuthEndpointsByIp() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/v1/auth/login");
        when(request.getRemoteAddr()).thenReturn("203.0.113.5");
        when(rateLimitService.checkRateLimit(any(), any(), any()))
                .thenReturn(new RateLimitService.RateLimitDecision(true, 119, 0));

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertTrue(allowed);
        verify(rateLimitService).checkRateLimit(eq("public"), eq("ip:203.0.113.5"), any());
        verifyNoInteractions(currentUserService);
    }
}
