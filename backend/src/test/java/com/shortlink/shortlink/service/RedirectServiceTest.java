package com.shortlink.shortlink.service;

import com.shortlink.shortlink.exception.ExpiredShortCodeException;
import com.shortlink.shortlink.exception.ResourceNotFoundException;
import com.shortlink.shortlink.model.Url;
import com.shortlink.shortlink.repository.UrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RedirectServiceTest {

    private UrlRepository urlRepository;
    private UrlCacheService urlCacheService;
    private RedirectService redirectService;

    @BeforeEach
    void setUp() {
        urlRepository = mock(UrlRepository.class);
        urlCacheService = mock(UrlCacheService.class);
        redirectService = new RedirectService(urlRepository, urlCacheService);
    }

    @Test
    void shouldResolveOriginalUrlFromCache() {
        when(urlCacheService.findByShortCode("abc1234")).thenReturn(Optional.of(
                new UrlCacheService.CachedUrl(
                        UUID.fromString("550e8400-e29b-41d4-a716-446655440000"),
                        "https://example.com/landing",
                        Instant.now().plusSeconds(300)
                )
        ));

        String originalUrl = redirectService.resolveOriginalUrl("abc1234");

        assertEquals("https://example.com/landing", originalUrl);
        verifyNoInteractions(urlRepository);
    }

    @Test
    void shouldResolveOriginalUrlFromDatabaseAndWarmCache() {
        Url url = new Url();
        url.setShortCode("abc1234");
        url.setOriginalUrl("https://example.com/landing");
        url.setActive(true);

        when(urlCacheService.findByShortCode("abc1234")).thenReturn(Optional.empty());
        when(urlRepository.findByShortCodeAndIsActiveTrue("abc1234")).thenReturn(Optional.of(url));

        String originalUrl = redirectService.resolveOriginalUrl("abc1234");

        assertEquals("https://example.com/landing", originalUrl);
        verify(urlCacheService).cacheUrl(url);
    }

    @Test
    void shouldThrowWhenShortCodeNotFound() {
        when(urlCacheService.findByShortCode("missing")).thenReturn(Optional.empty());
        when(urlRepository.findByShortCodeAndIsActiveTrue("missing")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> redirectService.resolveOriginalUrl("missing"));
    }

    @Test
    void shouldThrowWhenCachedShortCodeExpired() {
        when(urlCacheService.findByShortCode("expired1")).thenReturn(Optional.of(
                new UrlCacheService.CachedUrl(
                        UUID.fromString("550e8400-e29b-41d4-a716-446655440000"),
                        "https://example.com/landing",
                        Instant.now().minusSeconds(60)
                )
        ));

        assertThrows(ExpiredShortCodeException.class, () -> redirectService.resolveOriginalUrl("expired1"));
        verifyNoInteractions(urlRepository);
    }

    @Test
    void shouldThrowWhenDatabaseShortCodeExpired() {
        Url url = new Url();
        url.setOriginalUrl("https://example.com/landing");
        url.setActive(true);
        url.setExpiresAt(Instant.now().minusSeconds(60));

        when(urlCacheService.findByShortCode("expired1")).thenReturn(Optional.empty());
        when(urlRepository.findByShortCodeAndIsActiveTrue("expired1")).thenReturn(Optional.of(url));

        assertThrows(ExpiredShortCodeException.class, () -> redirectService.resolveOriginalUrl("expired1"));
    }
}
