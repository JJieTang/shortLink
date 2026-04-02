package com.shortlink.shortlink.service;

import com.shortlink.shortlink.repository.RefreshTokenRepository;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RefreshTokenCleanupServiceTest {

    @Test
    void shouldDeleteExpiredRefreshTokens() {
        RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
        RefreshTokenCleanupService cleanupService = new RefreshTokenCleanupService(refreshTokenRepository);

        cleanupService.deleteExpiredTokens();

        verify(refreshTokenRepository).deleteByExpiresAtBefore(any());
    }
}
