package com.shortlink.shortlink.service;

import com.shortlink.shortlink.dto.CreateUrlRequest;
import com.shortlink.shortlink.model.Url;
import com.shortlink.shortlink.model.User;
import com.shortlink.shortlink.repository.UrlRepository;
import com.shortlink.shortlink.repository.UserRepository;
import com.shortlink.shortlink.util.Base62Encoder;
import com.shortlink.shortlink.util.ReservedWords;
import com.shortlink.shortlink.util.UrlValidator;
import com.shortlink.shortlink.exception.ResourceNotFoundException;
import com.shortlink.shortlink.exception.InvalidRequestException;
import com.shortlink.shortlink.exception.AliasTakenException;
import com.shortlink.shortlink.exception.InvalidAliasException;
import com.shortlink.shortlink.exception.ShortCodeGenerationException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class UrlShorteningService {

    // TODO: Phase 3 删除，替换为 SecurityContext 获取当前用户
    static final UUID SEED_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String CUSTOM_ALIAS_PATTERN = "^[A-Za-z0-9_-]{3,30}$";

    private final Base62Encoder base62Encoder;
    private final UrlRepository urlRepository;
    private final UserRepository userRepository;
    private final UrlValidator urlValidator;
    private final ReservedWords reservedWords;
    private final UrlCacheService urlCacheService;
    private final Counter urlsCreatedCounter;
    private final String baseUrl;

    public UrlShorteningService(
            Base62Encoder base62Encoder,
            UrlRepository urlRepository,
            UserRepository userRepository,
            UrlValidator urlValidator,
            ReservedWords reservedWords,
            UrlCacheService urlCacheService,
            MeterRegistry meterRegistry,
            @Value("${app.base-url:http://localhost:8080}") String baseUrl) {
        this.base62Encoder = base62Encoder;
        this.urlRepository = urlRepository;
        this.userRepository = userRepository;
        this.urlValidator = urlValidator;
        this.reservedWords = reservedWords;
        this.urlCacheService = urlCacheService;
        this.urlsCreatedCounter = Counter.builder("shortlink_urls_created_total")
                .description("Total number of successfully created short URLs")
                .register(meterRegistry);
        this.baseUrl = baseUrl;
    }

    @Transactional
    public Url createShortUrl (CreateUrlRequest request) {
        urlValidator.validate(request.originalUrl());
        validateExpiration(request.expiresAt());

        String shortCode = resolveShortCode(request.customAlias());

        User user = userRepository.findById(SEED_USER_ID).orElseThrow(
                () -> new ResourceNotFoundException("Seed user not found."));

        Url url = new Url();
        url.setShortCode(shortCode);
        url.setOriginalUrl(request.originalUrl());
        url.setUser(user);
        url.setExpiresAt(request.expiresAt());
        url.setTotalClicks(0L);
        url.setActive(true);

        Url savedUrl = urlRepository.save(url);
        urlCacheService.cacheUrl(savedUrl);
        urlsCreatedCounter.increment();
        return savedUrl;
    }

    @Transactional(readOnly = true)
    public Url getUrl(String shortCode) {
        return urlRepository.findByShortCodeAndIsActiveTrue(shortCode).orElseThrow(
                () -> new ResourceNotFoundException("Short code not found: " + shortCode));
    }

    @Transactional
    public void deleteUrl (String shortCode) {
        Url url = getUrl(shortCode);
        url.setActive(false);
        urlCacheService.evict(shortCode);
    }

    @Transactional(readOnly = true)
    public Page<Url> listUrls(Pageable pageable) {
        return urlRepository.findByUser_IdAndIsActiveTrue(SEED_USER_ID, pageable);
    }

    public String getBaseUrl(){
        return baseUrl;
    }

    private void validateExpiration (Instant expiresAt) {
        if(expiresAt != null && !expiresAt.isAfter(Instant.now())){
            throw new InvalidRequestException("expiresAt must be in the future");
        }
    }

    private String resolveShortCode (String customAlias) {
        if (customAlias == null || customAlias.isBlank()) {
            return generateUniqueShortCode();
        }

        String normalizedAlias = customAlias.trim();
        validateAlias(normalizedAlias);

        if (urlRepository.existsByShortCode(normalizedAlias)) {
            throw new AliasTakenException("Alias already exists: " + normalizedAlias);
        }
        return normalizedAlias;
    }

    private void validateAlias (String alias) {
        if (!alias.matches(CUSTOM_ALIAS_PATTERN)) {
            throw new InvalidAliasException( "Alias must be 3-30 chars and contain only letters, digits, '-' or '_'");
        }

        if (reservedWords.isReserved(alias)) {
            throw new InvalidAliasException("Alias is reserved: " + alias);
        }
    }

    public String generateUniqueShortCode() {
        for (int attempt = 0; attempt < 3; attempt++) {
            String code = base62Encoder.generateRandomCode();
            if (!urlRepository.existsByShortCode(code)) {
                return code;
            }
        }
        throw new ShortCodeGenerationException("Short code generation failed after 3 attempts");
    }
}
