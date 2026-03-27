package com.shortlink.shortlink.controller;

import com.shortlink.shortlink.exception.GlobalExceptionHandler;
import com.shortlink.shortlink.service.RedirectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RedirectControllerTest {

    private MockMvc mockMvc;
    private RedirectService redirectService;

    @BeforeEach
    void setUp() {
        redirectService = mock(RedirectService.class);
        RedirectController redirectController = new RedirectController(redirectService);

        mockMvc = MockMvcBuilders.standaloneSetup(redirectController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldRedirectToOriginalUrl() throws Exception {
        when(redirectService.resolveOriginalUrl("abc1234")).thenReturn("https://example.com/landing");

        mockMvc.perform(get("/abc1234"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/landing"));
    }
}
