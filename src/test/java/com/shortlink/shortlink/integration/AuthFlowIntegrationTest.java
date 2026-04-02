package com.shortlink.shortlink.integration;

import com.shortlink.shortlink.model.RefreshToken;
import com.shortlink.shortlink.repository.RefreshTokenRepository;
import com.shortlink.shortlink.repository.UserRepository;
import com.shortlink.shortlink.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AuthFlowIntegrationTest extends AbstractIntegrationTest {

    private static final String USER_EMAIL = "auth-flow@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        userRepository.findByEmail(USER_EMAIL).ifPresent(userRepository::delete);
        refreshTokenRepository.deleteAll();
    }

    @Test
    void shouldRegisterLoginAndRefreshTokens() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "auth-flow@example.com",
                                  "password": "SecurePass123",
                                  "name": "Auth Flow"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("auth-flow@example.com"))
                .andExpect(jsonPath("$.name").value("Auth Flow"));

        String loginResponse = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "AUTH-FLOW@example.com",
                                  "password": "SecurePass123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", not(blankOrNullString())))
                .andExpect(jsonPath("$.refreshToken", not(blankOrNullString())))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String originalRefreshToken = com.jayway.jsonpath.JsonPath.read(loginResponse, "$.refreshToken");
        String originalRefreshTokenHash = jwtTokenProvider.hashToken(originalRefreshToken);

        Optional<RefreshToken> storedOriginalToken = refreshTokenRepository.findByTokenHash(originalRefreshTokenHash);
        assertFalse(storedOriginalToken.isEmpty());
        assertEquals(1L, refreshTokenRepository.count());

        String refreshResponse = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "%s"
                                }
                                """.formatted(originalRefreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", not(blankOrNullString())))
                .andExpect(jsonPath("$.refreshToken", not(blankOrNullString())))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String rotatedRefreshToken = com.jayway.jsonpath.JsonPath.read(refreshResponse, "$.refreshToken");

        assertFalse(refreshTokenRepository.findByTokenHash(originalRefreshTokenHash).isPresent());
        assertFalse(refreshTokenRepository.findByTokenHash(jwtTokenProvider.hashToken(rotatedRefreshToken)).isEmpty());
        assertEquals(1L, refreshTokenRepository.count());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "%s"
                                }
                                """.formatted(originalRefreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("AUTHENTICATION_FAILED"));
    }

    @Test
    void shouldRejectLoginWithWrongPassword() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "auth-flow@example.com",
                                  "password": "SecurePass123",
                                  "name": "Auth Flow"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "auth-flow@example.com",
                                  "password": "WrongPass123"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("AUTHENTICATION_FAILED"));
    }
}
