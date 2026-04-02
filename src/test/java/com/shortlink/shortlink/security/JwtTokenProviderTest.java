package com.shortlink.shortlink.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(
                "this-is-a-test-secret-with-at-least-32-bytes",
                "shortlink-test",
                Duration.ofMinutes(15),
                Duration.ofDays(7)
        );
    }

    @Test
    void shouldGenerateAndParseAccessToken() {
        UUID userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

        String accessToken = jwtTokenProvider.generateAccessToken(userId, "user@example.com", "USER");
        AuthenticatedUser authenticatedUser = jwtTokenProvider.getAuthenticatedUser(accessToken);
        Instant expiration = jwtTokenProvider.getExpiration(accessToken);

        assertEquals(userId, authenticatedUser.userId());
        assertEquals("user@example.com", authenticatedUser.email());
        assertEquals("USER", authenticatedUser.role());
        assertTrue(expiration.isAfter(Instant.now()));
    }

    @Test
    void shouldRejectRefreshTokenWhenParsedAsAccessToken() {
        String refreshToken = jwtTokenProvider.generateRefreshToken(
                UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        );

        assertThrows(IllegalArgumentException.class, () -> jwtTokenProvider.getAuthenticatedUser(refreshToken));
    }

    @Test
    void shouldGenerateDistinctRefreshTokensForSameUser() {
        UUID userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

        String firstRefreshToken = jwtTokenProvider.generateRefreshToken(userId);
        String secondRefreshToken = jwtTokenProvider.generateRefreshToken(userId);

        assertNotEquals(firstRefreshToken, secondRefreshToken);
    }

    @Test
    void shouldHashTokensDeterministically() {
        String token = "sample.jwt.token";

        String firstHash = jwtTokenProvider.hashToken(token);
        String secondHash = jwtTokenProvider.hashToken(token);

        assertEquals(firstHash, secondHash);
        assertNotEquals(firstHash, jwtTokenProvider.hashToken("different.jwt.token"));
    }

    @Test
    void shouldRejectDefaultSecretOutsideDevProfile() {
        assertThrows(IllegalStateException.class, () -> new JwtTokenProvider(
                JwtTokenProvider.DEFAULT_DEV_SECRET,
                "shortlink-test",
                Duration.ofMinutes(15),
                Duration.ofDays(7),
                false
        ));
    }
}
