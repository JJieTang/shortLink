package com.shortlink.shortlink.integration;

import com.shortlink.shortlink.repository.UrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class RateLimitIntegrationTest extends AbstractIntegrationTest {

    private static final String OWNER_EMAIL = "rate-limit-owner@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UrlRepository urlRepository;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private String ownerAccessToken;

    @DynamicPropertySource
    static void rateLimitProperties(DynamicPropertyRegistry registry) {
        registry.add("app.rate-limit.public.capacity", () -> 2);
        registry.add("app.rate-limit.public.refill-period", () -> "1m");
        registry.add("app.rate-limit.management.capacity", () -> 2);
        registry.add("app.rate-limit.management.refill-period", () -> "1m");
    }

    @BeforeEach
    void setUp() {
        urlRepository.deleteAll();
        userRepository.findByEmail(OWNER_EMAIL).ifPresent(userRepository::delete);
        ownerAccessToken = issueAccessToken(OWNER_EMAIL, "Rate Limit Owner");
        clearRateLimitKeys();
    }

    @Test
    void shouldReturn429ForPublicRedirectAfterCapacityIsExceeded() throws Exception {
        mockMvc.perform(post("/api/v1/urls")
                        .header("Authorization", bearer(ownerAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "originalUrl": "https://example.com/landing",
                                  "customAlias": "rate-limit-public"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/rate-limit-public"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/landing"));

        mockMvc.perform(get("/rate-limit-public"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/landing"));

        mockMvc.perform(get("/rate-limit-public"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(jsonPath("$.error", is("RATE_LIMITED")))
                .andExpect(jsonPath("$.path", is("/rate-limit-public")));
    }

    @Test
    void shouldReturn429ForManagementRequestsAfterCapacityIsExceeded() throws Exception {
        mockMvc.perform(get("/api/v1/urls")
                        .header("Authorization", bearer(ownerAccessToken)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/urls")
                        .header("Authorization", bearer(ownerAccessToken)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/urls")
                        .header("Authorization", bearer(ownerAccessToken)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(jsonPath("$.error", is("RATE_LIMITED")))
                .andExpect(jsonPath("$.path", is("/api/v1/urls")));
    }

    private void clearRateLimitKeys() {
        Set<String> keys = stringRedisTemplate.keys("rate-limit:*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }
}
