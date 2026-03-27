package com.shortlink.shortlink.service;

import com.shortlink.shortlink.exception.ExpiredShortCodeException;
import com.shortlink.shortlink.exception.ResourceNotFoundException;
import com.shortlink.shortlink.model.Url;
import com.shortlink.shortlink.repository.UrlRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class RedirectService {

    private final UrlRepository urlRepository;

    public RedirectService(UrlRepository urlRepository) {
        this.urlRepository = urlRepository;
    }

    @Transactional(readOnly = true)
    public String resolveOriginalUrl (String shortCode) {
        Url url = urlRepository.findByShortCodeAndIsActiveTrue(shortCode).orElseThrow(
                () -> new ResourceNotFoundException("Short code not found: " + shortCode)
        );

        if (url.getExpiresAt() != null && !url.getExpiresAt().isAfter(Instant.now())) {
            throw new ExpiredShortCodeException(shortCode);
        }

        return url.getOriginalUrl();
    }
}
