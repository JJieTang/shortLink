package com.shortlink.shortlink.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortlink.shortlink.dto.CreateUrlRequest;
import com.shortlink.shortlink.exception.GlobalExceptionHandler;
import com.shortlink.shortlink.model.Url;
import com.shortlink.shortlink.service.UrlShorteningService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UrlControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private MockMvc mockMvc;
    private UrlShorteningService urlShorteningService;

    @BeforeEach
    void setUp() {
        urlShorteningService = mock(UrlShorteningService.class);
        UrlController urlController = new UrlController(urlShorteningService);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(urlController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setValidator(validator)
                .build();
    }

    @Test
    void shouldCreateUrl() throws Exception {
        Url url = new Url();
        url.setId(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"));
        url.setShortCode("aB3xK7c");
        url.setOriginalUrl("https://example.com/path");
        url.setTotalClicks(0L);
        url.setCreatedAt(Instant.parse("2026-03-27T08:00:00Z"));
        url.setUpdatedAt(Instant.parse("2026-03-27T08:00:00Z"));

        when(urlShorteningService.createShortUrl(any(CreateUrlRequest.class))).thenReturn(url);
        when(urlShorteningService.getBaseUrl()).thenReturn("http://localhost:8080");

        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateUrlRequest("https://example.com/path", null, null)
                        )))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortCode").value("aB3xK7c"))
                .andExpect(jsonPath("$.shortUrl").value("http://localhost:8080/aB3xK7c"))
                .andExpect(jsonPath("$.originalUrl").value("https://example.com/path"));
    }

    @Test
    void shouldReturnValidationErrorForBlankOriginalUrl() throws Exception {
        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"originalUrl":"","customAlias":"promo-link"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.path").value("/api/v1/urls"));
    }

    @Test
    void shouldGetUrl() throws Exception {
        Url url = new Url();
        url.setId(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"));
        url.setShortCode("aB3xK7c");
        url.setOriginalUrl("https://example.com/path");
        url.setTotalClicks(12L);
        url.setCreatedAt(Instant.parse("2026-03-27T08:00:00Z"));
        url.setUpdatedAt(Instant.parse("2026-03-27T08:00:00Z"));

        when(urlShorteningService.getUrl("aB3xK7c")).thenReturn(url);
        when(urlShorteningService.getBaseUrl()).thenReturn("http://localhost:8080");

        mockMvc.perform(get("/api/v1/urls/aB3xK7c"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortCode").value("aB3xK7c"))
                .andExpect(jsonPath("$.totalClicks").value(12));
    }

    @Test
    void shouldDeleteUrl() throws Exception {
        mockMvc.perform(delete("/api/v1/urls/aB3xK7c"))
                .andExpect(status().isNoContent());
    }

    @Test
    void shouldListUrls() throws Exception {
        Url first = new Url();
        first.setId(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"));
        first.setShortCode("aB3xK7c");
        first.setOriginalUrl("https://example.com/path-1");
        first.setTotalClicks(12L);
        first.setCreatedAt(Instant.parse("2026-03-27T08:00:00Z"));
        first.setUpdatedAt(Instant.parse("2026-03-27T08:00:00Z"));

        Url second = new Url();
        second.setId(UUID.fromString("550e8400-e29b-41d4-a716-446655440001"));
        second.setShortCode("dE4yP9m");
        second.setOriginalUrl("https://example.com/path-2");
        second.setTotalClicks(4L);
        second.setCreatedAt(Instant.parse("2026-03-27T09:00:00Z"));
        second.setUpdatedAt(Instant.parse("2026-03-27T09:00:00Z"));

        when(urlShorteningService.listUrls(any())).thenReturn(
                new PageImpl<>(List.of(first, second), PageRequest.of(0, 2), 2)
        );
        when(urlShorteningService.getBaseUrl()).thenReturn("http://localhost:8080");

        mockMvc.perform(get("/api/v1/urls")
                        .param("page", "0")
                        .param("size", "2")
                        .param("sort", "createdAt,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].shortCode").value("aB3xK7c"))
                .andExpect(jsonPath("$.content[1].shortCode").value("dE4yP9m"))
                .andExpect(jsonPath("$.totalElements").value(2));
    }
}
