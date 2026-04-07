package com.shortlink.shortlink.service;

import com.shortlink.shortlink.exception.ExpiredShortCodeException;
import com.shortlink.shortlink.exception.ResourceNotFoundException;
import com.shortlink.shortlink.model.Url;
import com.shortlink.shortlink.repository.UrlRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class RedirectService {

    private final UrlRepository urlRepository;
    private final UrlCacheService urlCacheService;

    public RedirectService(UrlRepository urlRepository, UrlCacheService urlCacheService) {
        this.urlRepository = urlRepository;
        this.urlCacheService = urlCacheService;
    }

    @Transactional(readOnly = true)
    public String resolveOriginalUrl (String shortCode) {
        return resolveRedirectTarget(shortCode).originalUrl();
    }

    @Transactional(readOnly = true)
    public RedirectTarget resolveRedirectTarget(String shortCode) {
        UrlCacheService.CachedUrl cachedUrl = urlCacheService.findByShortCode(shortCode)
                .orElse(null);

        if (cachedUrl != null) {
            validateNotExpired(shortCode, cachedUrl.expiresAt());
            return new RedirectTarget(cachedUrl.urlId(), shortCode, cachedUrl.originalUrl(), true);
        }

        Url url = urlRepository.findByShortCodeAndIsActiveTrue(shortCode).orElseThrow(
                () -> new ResourceNotFoundException("Short code not found: " + shortCode)
        );

        validateNotExpired(shortCode, url.getExpiresAt());
        urlCacheService.cacheUrl(url);

        return new RedirectTarget(url.getId(), url.getShortCode(), url.getOriginalUrl(), false);
    }

    private void validateNotExpired(String shortCode, Instant expiresAt) {
        if (expiresAt != null && !expiresAt.isAfter(Instant.now())) {
            throw new ExpiredShortCodeException(shortCode);
        }
    }

    public record RedirectTarget(UUID urlId, String shortCode, String originalUrl, boolean cacheHit) {
    }
}
