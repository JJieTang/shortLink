package com.shortlink.shortlink.service;

import com.shortlink.shortlink.dto.CreateUrlRequest;
import com.shortlink.shortlink.exception.AliasTakenException;
import com.shortlink.shortlink.exception.InvalidAliasException;
import com.shortlink.shortlink.exception.InvalidRequestException;
import com.shortlink.shortlink.exception.ShortCodeGenerationException;
import com.shortlink.shortlink.model.Url;
import com.shortlink.shortlink.model.User;
import com.shortlink.shortlink.repository.UrlRepository;
import com.shortlink.shortlink.security.CurrentUserService;
import com.shortlink.shortlink.util.Base62Encoder;
import com.shortlink.shortlink.util.ReservedWords;
import com.shortlink.shortlink.util.UrlValidator;
import com.shortlink.shortlink.config.ShortlinkMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UrlShorteningServiceTest {

    private Base62Encoder base62Encoder;
    private UrlRepository urlRepository;
    private CurrentUserService currentUserService;
    private UrlCacheService urlCacheService;
    private SimpleMeterRegistry meterRegistry;
    private UrlShorteningService urlShorteningService;

    @BeforeEach
    void setUp() {
        base62Encoder = mock(Base62Encoder.class);
        urlRepository = mock(UrlRepository.class);
        currentUserService = mock(CurrentUserService.class);
        urlCacheService = mock(UrlCacheService.class);
        meterRegistry = new SimpleMeterRegistry();
        urlShorteningService = new UrlShorteningService(
                base62Encoder,
                urlRepository,
                currentUserService,
                new UrlValidator(),
                new ReservedWords(),
                urlCacheService,
                meterRegistry,
                "http://localhost:8080"
        );
    }

    @Test
    void shouldCreateShortUrlWithGeneratedCode() {
        User user = new User();
        user.setId(java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655440000"));

        when(base62Encoder.generateRandomCode()).thenReturn("abc1234");
        when(urlRepository.existsByShortCode("abc1234")).thenReturn(false);
        when(currentUserService.getCurrentUserReference()).thenReturn(user);
        when(urlRepository.save(any(Url.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Url created = urlShorteningService.createShortUrl(
                new CreateUrlRequest("https://example.com/page", null, Instant.now().plusSeconds(3600))
        );

        assertEquals("abc1234", created.getShortCode());
        assertEquals("https://example.com/page", created.getOriginalUrl());
        assertSame(user, created.getUser());
        assertEquals(0L, created.getTotalClicks());
        verify(urlRepository).save(any(Url.class));
        verify(urlCacheService).cacheUrl(created);
        assertEquals(
                1.0,
                meterRegistry.get(ShortlinkMetrics.URLS_CREATED_TOTAL)
                        .tag(ShortlinkMetrics.URL_TYPE_TAG, ShortlinkMetrics.URL_TYPE_GENERATED)
                        .counter()
                        .count()
        );
    }

    @Test
    void shouldRejectReservedAlias() {
        assertThrows(
                InvalidAliasException.class,
                () -> urlShorteningService.createShortUrl(
                        new CreateUrlRequest("https://example.com/page", "admin", null)
                )
        );
    }

    @Test
    void shouldRejectTakenAlias() {
        when(urlRepository.existsByShortCode("promo-link")).thenReturn(true);

        assertThrows(
                AliasTakenException.class,
                () -> urlShorteningService.createShortUrl(
                        new CreateUrlRequest("https://example.com/page", "promo-link", null)
                )
        );
    }

    @Test
    void shouldRejectPastExpiration() {
        assertThrows(
                InvalidRequestException.class,
                () -> urlShorteningService.createShortUrl(
                        new CreateUrlRequest("https://example.com/page", null, Instant.now().minusSeconds(60))
                )
        );
    }

    @Test
    void shouldRetryThreeTimesBeforeFailingShortCodeGeneration() {
        when(base62Encoder.generateRandomCode()).thenReturn("dup0001", "dup0002", "dup0003");
        when(urlRepository.existsByShortCode(any(String.class))).thenReturn(true);

        assertThrows(ShortCodeGenerationException.class, () -> urlShorteningService.generateUniqueShortCode());
        verify(urlRepository, times(3)).existsByShortCode(any(String.class));
    }

    @Test
    void shouldSoftDeleteUrl() {
        Url url = new Url();
        url.setActive(true);
        when(currentUserService.getCurrentUserId()).thenReturn(java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655440000"));
        when(urlRepository.findByShortCodeAndUser_IdAndIsActiveTrue(
                "abc1234",
                java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        )).thenReturn(Optional.of(url));

        urlShorteningService.deleteUrl("abc1234");

        assertFalse(url.getActive());
        verify(urlCacheService).evict("abc1234");
    }

    @Test
    void shouldListUrlsForCurrentUser() {
        Url first = new Url();
        first.setShortCode("abc1234");
        Url second = new Url();
        second.setShortCode("def5678");

        PageRequest pageable = PageRequest.of(0, 2);
        Page<Url> page = new PageImpl<>(List.of(first, second), pageable, 2);
        java.util.UUID currentUserId = java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        when(currentUserService.getCurrentUserId()).thenReturn(currentUserId);
        when(urlRepository.findByUser_IdAndIsActiveTrue(currentUserId, pageable)).thenReturn(page);

        Page<Url> result = urlShorteningService.listUrls(pageable);

        assertEquals(2, result.getContent().size());
        assertEquals("abc1234", result.getContent().get(0).getShortCode());
        assertEquals("def5678", result.getContent().get(1).getShortCode());
        verify(urlRepository).findByUser_IdAndIsActiveTrue(currentUserId, pageable);
    }
}
