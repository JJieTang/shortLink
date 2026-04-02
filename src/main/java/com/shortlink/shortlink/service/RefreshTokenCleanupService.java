package com.shortlink.shortlink.service;

import com.shortlink.shortlink.repository.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class RefreshTokenCleanupService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanupService.class);

    private final RefreshTokenRepository refreshTokenRepository;

    public RefreshTokenCleanupService(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Transactional
    @Scheduled(
            fixedDelayString = "${app.jwt.cleanup-interval}",
            initialDelayString = "${app.jwt.cleanup-interval}"
    )
    public void deleteExpiredTokens() {
        long deletedCount = refreshTokenRepository.deleteByExpiresAtBefore(Instant.now());
        if (deletedCount > 0) {
            log.info("Deleted {} expired refresh tokens", deletedCount);
        }
    }
}
