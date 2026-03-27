package com.shortlink.shortlink.service;

import com.shortlink.shortlink.exception.ExpiredShortCodeException;
import com.shortlink.shortlink.exception.ResourceNotFoundException;
import com.shortlink.shortlink.model.Url;
import com.shortlink.shortlink.repository.UrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedirectServiceTest {

    private UrlRepository urlRepository;
    private RedirectService redirectService;

    @BeforeEach
    void setUp() {
        urlRepository = mock(UrlRepository.class);
        redirectService = new RedirectService(urlRepository);
    }

    @Test
    void shouldResolveOriginalUrl() {
        Url url = new Url();
        url.setOriginalUrl("https://example.com/landing");
        url.setActive(true);

        when(urlRepository.findByShortCodeAndIsActiveTrue("abc1234")).thenReturn(Optional.of(url));

        String originalUrl = redirectService.resolveOriginalUrl("abc1234");

        assertEquals("https://example.com/landing", originalUrl);
    }

    @Test
    void shouldThrowWhenShortCodeNotFound() {
        when(urlRepository.findByShortCodeAndIsActiveTrue("missing")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> redirectService.resolveOriginalUrl("missing"));
    }

    @Test
    void shouldThrowWhenShortCodeExpired() {
        Url url = new Url();
        url.setOriginalUrl("https://example.com/landing");
        url.setActive(true);
        url.setExpiresAt(Instant.now().minusSeconds(60));

        when(urlRepository.findByShortCodeAndIsActiveTrue("expired1")).thenReturn(Optional.of(url));

        assertThrows(ExpiredShortCodeException.class, () -> redirectService.resolveOriginalUrl("expired1"));
    }
}
