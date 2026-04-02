package com.shortlink.shortlink.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    static final String DEFAULT_DEV_SECRET = "replace-this-dev-secret-with-at-least-32-characters";

    private static final String EMAIL_CLAIM = "email";
    private static final String ROLE_CLAIM = "role";
    private static final String TOKEN_TYPE_CLAIM = "type";
    private static final String ACCESS_TOKEN_TYPE = "access";
    private static final String REFRESH_TOKEN_TYPE = "refresh";
    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    private final SecretKey signingKey;
    private final String issuer;
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;

    @Autowired
    public JwtTokenProvider(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.issuer}") String issuer,
            @Value("${app.jwt.access-token-ttl}") Duration accessTokenTtl,
            @Value("${app.jwt.refresh-token-ttl}") Duration refreshTokenTtl,
            Environment environment) {
        this(secret, issuer, accessTokenTtl, refreshTokenTtl, environment.acceptsProfiles(Profiles.of("dev")));
    }

    JwtTokenProvider(
            String secret,
            String issuer,
            Duration accessTokenTtl,
            Duration refreshTokenTtl) {
        this(secret, issuer, accessTokenTtl, refreshTokenTtl, true);
    }

    JwtTokenProvider(
            String secret,
            String issuer,
            Duration accessTokenTtl,
            Duration refreshTokenTtl,
            boolean devProfileActive) {
        if (DEFAULT_DEV_SECRET.equals(secret)) {
            if (devProfileActive) {
                log.warn("JWT secret is using the default development fallback. Set JWT_SECRET before sharing or deploying this app.");
            } else {
                throw new IllegalStateException("JWT secret must be overridden outside the dev profile");
            }
        }

        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 bytes");
        }

        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.issuer = issuer;
        this.accessTokenTtl = accessTokenTtl;
        this.refreshTokenTtl = refreshTokenTtl;
    }

    public String generateAccessToken(UUID userId, String email, String role) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(accessTokenTtl);

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId.toString())
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .claim(EMAIL_CLAIM, email)
                .claim(ROLE_CLAIM, role)
                .claim(TOKEN_TYPE_CLAIM, ACCESS_TOKEN_TYPE)
                .signWith(signingKey)
                .compact();
    }

    public String generateRefreshToken(UUID userId) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(refreshTokenTtl);

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId.toString())
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .claim(TOKEN_TYPE_CLAIM, REFRESH_TOKEN_TYPE)
                .signWith(signingKey)
                .compact();
    }

    public AuthenticatedUser getAuthenticatedUser(String token) {
        Claims claims = parseAccessTokenClaims(token);
        return new AuthenticatedUser(
                UUID.fromString(claims.getSubject()),
                claims.get(EMAIL_CLAIM, String.class),
                claims.get(ROLE_CLAIM, String.class)
        );
    }

    public UUID getRefreshTokenUserId(String token) {
        Claims claims = parseRefreshTokenClaims(token);
        return UUID.fromString(claims.getSubject());
    }

    public Instant getExpiration(String token) {
        return parseClaims(token).getExpiration().toInstant();
    }

    public String hashToken(String token) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] digest = messageDigest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm not available", exception);
        }
    }

    private Claims parseAccessTokenClaims(String token) {
        Claims claims = parseClaims(token);
        validateTokenType(claims, ACCESS_TOKEN_TYPE);
        return claims;
    }

    private Claims parseRefreshTokenClaims(String token) {
        Claims claims = parseClaims(token);
        validateTokenType(claims, REFRESH_TOKEN_TYPE);
        return claims;
    }

    private Claims parseClaims(String token) {
        try {
            Jws<Claims> jws = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token);
            return jws.getPayload();
        } catch (JwtException | IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid JWT token", exception);
        }
    }

    private void validateTokenType(Claims claims, String expectedType) {
        String actualType = claims.get(TOKEN_TYPE_CLAIM, String.class);
        if (!expectedType.equals(actualType)) {
            throw new IllegalArgumentException("Unexpected JWT token type");
        }
    }
}
