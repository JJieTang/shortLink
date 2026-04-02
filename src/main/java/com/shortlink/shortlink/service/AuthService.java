package com.shortlink.shortlink.service;

import com.shortlink.shortlink.dto.auth.AuthResponse;
import com.shortlink.shortlink.dto.auth.LoginRequest;
import com.shortlink.shortlink.dto.auth.RefreshTokenRequest;
import com.shortlink.shortlink.dto.auth.RegisterRequest;
import com.shortlink.shortlink.dto.auth.RegisterResponse;
import com.shortlink.shortlink.exception.AuthenticationFailedException;
import com.shortlink.shortlink.exception.EmailAlreadyExistsException;
import com.shortlink.shortlink.model.RefreshToken;
import com.shortlink.shortlink.model.User;
import com.shortlink.shortlink.repository.RefreshTokenRepository;
import com.shortlink.shortlink.repository.UserRepository;
import com.shortlink.shortlink.security.JwtTokenProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        if (userRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new EmailAlreadyExistsException("Email already exists: " + normalizedEmail);
        }

        User user = new User();
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setName(request.name().trim());
        user.setRole("USER");
        user.setDailyQuota(100);

        User savedUser = userRepository.save(user);
        return RegisterResponse.from(savedUser);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new AuthenticationFailedException("Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new AuthenticationFailedException("Invalid email or password");
        }

        return issueTokens(user);
    }

    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        String rawRefreshToken = request.refreshToken().trim();

        try {
            UUID userId = jwtTokenProvider.getRefreshTokenUserId(rawRefreshToken);
            String refreshTokenHash = jwtTokenProvider.hashToken(rawRefreshToken);

            RefreshToken storedToken = refreshTokenRepository.findByTokenHash(refreshTokenHash)
                    .orElseThrow(() -> new AuthenticationFailedException("Refresh token is invalid"));

            if (!storedToken.getUser().getId().equals(userId)) {
                throw new AuthenticationFailedException("Refresh token is invalid");
            }

            if (storedToken.getExpiresAt().isBefore(Instant.now())) {
                refreshTokenRepository.delete(storedToken);
                throw new AuthenticationFailedException("Refresh token has expired");
            }

            User user = storedToken.getUser();
            refreshTokenRepository.delete(storedToken);
            return issueTokens(user);
        } catch (IllegalArgumentException exception) {
            throw new AuthenticationFailedException("Refresh token is invalid");
        }
    }

    private AuthResponse issueTokens(User user) {
        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId(),
                user.getEmail(),
                user.getRole()
        );
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        RefreshToken refreshTokenEntity = new RefreshToken();
        refreshTokenEntity.setUser(user);
        refreshTokenEntity.setTokenHash(jwtTokenProvider.hashToken(refreshToken));
        refreshTokenEntity.setExpiresAt(jwtTokenProvider.getExpiration(refreshToken));
        refreshTokenRepository.save(refreshTokenEntity);

        return AuthResponse.of(accessToken, refreshToken);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
