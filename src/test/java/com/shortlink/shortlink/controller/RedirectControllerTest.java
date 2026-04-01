package com.shortlink.shortlink.controller;

import com.shortlink.shortlink.exception.GlobalExceptionHandler;
import com.shortlink.shortlink.service.ClickEventPublisher;
import com.shortlink.shortlink.service.RedirectService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RedirectControllerTest {

    private MockMvc mockMvc;
    private RedirectService redirectService;
    private ClickEventPublisher clickEventPublisher;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        redirectService = mock(RedirectService.class);
        clickEventPublisher = mock(ClickEventPublisher.class);
        meterRegistry = new SimpleMeterRegistry();
        Counter redirectsCounter = meterRegistry.counter("shortlink_redirects_total");
        Timer redirectLatencyTimer = meterRegistry.timer("shortlink_redirect_latency_seconds");
        RedirectController redirectController = new RedirectController(
                redirectService,
                clickEventPublisher,
                redirectsCounter,
                redirectLatencyTimer
        );

        mockMvc = MockMvcBuilders.standaloneSetup(redirectController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldRedirectToOriginalUrl() throws Exception {
        when(redirectService.resolveRedirectTarget("abc1234")).thenReturn(
                new RedirectService.RedirectTarget(
                        UUID.fromString("550e8400-e29b-41d4-a716-446655440000"),
                        "abc1234",
                        "https://example.com/landing"
                )
        );

        mockMvc.perform(get("/abc1234"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/landing"));

        verify(clickEventPublisher).publish(any());
        org.junit.jupiter.api.Assertions.assertEquals(
                1.0,
                meterRegistry.get("shortlink_redirects_total").counter().count()
        );
        org.junit.jupiter.api.Assertions.assertEquals(
                1L,
                meterRegistry.get("shortlink_redirect_latency_seconds").timer().count()
        );
    }
}
