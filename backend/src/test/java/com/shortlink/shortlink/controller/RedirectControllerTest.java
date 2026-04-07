package com.shortlink.shortlink.controller;

import com.shortlink.shortlink.exception.GlobalExceptionHandler;
import com.shortlink.shortlink.security.CurrentUserService;
import com.shortlink.shortlink.service.ClickEventPublisher;
import com.shortlink.shortlink.service.RateLimitService;
import com.shortlink.shortlink.service.RedirectService;
import com.shortlink.shortlink.config.ShortlinkMetrics;
import com.shortlink.shortlink.config.RateLimitInterceptor;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RedirectControllerTest {

    private MockMvc mockMvc;
    private RedirectService redirectService;
    private ClickEventPublisher clickEventPublisher;
    private RateLimitService rateLimitService;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        redirectService = mock(RedirectService.class);
        clickEventPublisher = mock(ClickEventPublisher.class);
        rateLimitService = mock(RateLimitService.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        meterRegistry = new SimpleMeterRegistry();
        RedirectController redirectController = new RedirectController(
                redirectService,
                clickEventPublisher,
                meterRegistry
        );
        when(rateLimitService.checkRateLimit(anyString(), anyString(), any()))
                .thenReturn(new RateLimitService.RateLimitDecision(true, 119, 0));
        RateLimitInterceptor rateLimitInterceptor = new RateLimitInterceptor(
                rateLimitService,
                currentUserService,
                120,
                java.time.Duration.ofMinutes(1),
                300,
                java.time.Duration.ofMinutes(1)
        );

        mockMvc = MockMvcBuilders.standaloneSetup(redirectController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .addInterceptors(rateLimitInterceptor)
                .build();
    }

    @Test
    void shouldRedirectToOriginalUrl() throws Exception {
        when(redirectService.resolveRedirectTarget("abc1234")).thenReturn(
                new RedirectService.RedirectTarget(
                        UUID.fromString("550e8400-e29b-41d4-a716-446655440000"),
                        "abc1234",
                        "https://example.com/landing",
                        true
                )
        );

        mockMvc.perform(get("/abc1234"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/landing"));

        verify(rateLimitService).checkRateLimit(eq("public"), eq("ip:127.0.0.1"), any());
        verify(clickEventPublisher).publish(any());
        assertEquals(
                1.0,
                meterRegistry.get(ShortlinkMetrics.REDIRECTS_TOTAL)
                        .tag(ShortlinkMetrics.STATUS_TAG, "302")
                        .tag(ShortlinkMetrics.CACHE_RESULT_TAG, ShortlinkMetrics.CACHE_HIT)
                        .counter()
                        .count()
        );
        assertEquals(
                1L,
                meterRegistry.get(ShortlinkMetrics.REDIRECT_LATENCY)
                        .tag(ShortlinkMetrics.STATUS_TAG, "302")
                        .tag(ShortlinkMetrics.CACHE_RESULT_TAG, ShortlinkMetrics.CACHE_HIT)
                        .timer()
                        .count()
        );
    }

    @Test
    void shouldStopRedirectLatencyTimerBeforePublishingClickEvent() throws Exception {
        when(redirectService.resolveRedirectTarget("latency123")).thenReturn(
                new RedirectService.RedirectTarget(
                        UUID.fromString("550e8400-e29b-41d4-a716-446655440010"),
                        "latency123",
                        "https://example.com/latency",
                        false
                )
        );
        doAnswer(invocation -> {
            assertEquals(
                    1L,
                    meterRegistry.get(ShortlinkMetrics.REDIRECT_LATENCY)
                            .tag(ShortlinkMetrics.STATUS_TAG, "302")
                            .tag(ShortlinkMetrics.CACHE_RESULT_TAG, ShortlinkMetrics.CACHE_MISS)
                            .timer()
                            .count()
            );
            return null;
        }).when(clickEventPublisher).publish(any());

        mockMvc.perform(get("/latency123"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/latency"));

        verify(clickEventPublisher).publish(any());
    }

    @Test
    void shouldReturnTooManyRequestsWhenRedirectRateLimitIsExceeded() throws Exception {
        when(rateLimitService.checkRateLimit(anyString(), anyString(), any()))
                .thenReturn(new RateLimitService.RateLimitDecision(false, 0, 3));

        mockMvc.perform(get("/abc1234"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "3"))
                .andExpect(jsonPath("$.error").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.path").value("/abc1234"));

        verify(rateLimitService).checkRateLimit(eq("public"), eq("ip:127.0.0.1"), any());
        verifyNoInteractions(redirectService);
        verifyNoInteractions(clickEventPublisher);
    }
}
