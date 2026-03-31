package com.shortlink.shortlink.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class RedisCacheIntegrationTest extends AbstractIntegrationTest {

    private static final String CACHE_KEY_PREFIX = "url:";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    void setUp() {
        Set<String> cacheKeys = stringRedisTemplate.keys(CACHE_KEY_PREFIX + "*");
        if (cacheKeys != null && !cacheKeys.isEmpty()) {
            stringRedisTemplate.delete(cacheKeys);
        }
    }

    @Test
    void shouldCacheOnCreateAndEvictOnDelete() throws Exception {
        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "originalUrl": "https://example.com/cached",
                                  "customAlias": "cache-link"
                                }
                                """))
                .andExpect(status().isCreated());

        Map<Object, Object> cachedEntries = stringRedisTemplate.opsForHash().entries(cacheKey("cache-link"));
        assertEquals("https://example.com/cached", cachedEntries.get("originalUrl"));

        mockMvc.perform(delete("/api/v1/urls/cache-link"))
                .andExpect(status().isNoContent());

        assertFalse(Boolean.TRUE.equals(stringRedisTemplate.hasKey(cacheKey("cache-link"))));
    }

    @Test
    void shouldWarmCacheAfterRedirectCacheMiss() throws Exception {
        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "originalUrl": "https://example.com/warm",
                                  "customAlias": "warm-link"
                                }
                                """))
                .andExpect(status().isCreated());

        stringRedisTemplate.delete(cacheKey("warm-link"));
        assertFalse(Boolean.TRUE.equals(stringRedisTemplate.hasKey(cacheKey("warm-link"))));

        mockMvc.perform(get("/warm-link"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/warm"));

        Map<Object, Object> cachedEntries = stringRedisTemplate.opsForHash().entries(cacheKey("warm-link"));
        assertEquals("https://example.com/warm", cachedEntries.get("originalUrl"));
        assertNotNull(stringRedisTemplate.getExpire(cacheKey("warm-link")));
    }

    private String cacheKey(String shortCode) {
        return CACHE_KEY_PREFIX + shortCode;
    }
}
