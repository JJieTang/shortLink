package com.shortlink.shortlink.integration;

import com.shortlink.shortlink.repository.ClickEventRepository;
import com.shortlink.shortlink.repository.UrlRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class RedisDegradationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UrlRepository urlRepository;

    @Autowired
    private ClickEventRepository clickEventRepository;

    @Autowired
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        clickEventRepository.deleteAll();
        urlRepository.deleteAll();
    }

    @Test
    void shouldKeepRedirectWorkingWhenRedisOperationsFail() throws Exception {
        double redirectsBefore = meterRegistry.get("shortlink_redirects_total").counter().count();
        double cacheMissesBefore = meterRegistry.get("shortlink_cache_misses_total").counter().count();
        double droppedEventsBefore = meterRegistry.get("shortlink_click_events_dropped_total").counter().count();

        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "originalUrl": "https://example.com/resilient",
                                  "customAlias": "resilient-link"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/resilient-link")
                        .header("X-Forwarded-For", "203.0.113.99"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/resilient"));

        assertEquals(1L, urlRepository.count());
        assertEquals(0L, clickEventRepository.count());
        assertEquals(redirectsBefore + 1.0, meterRegistry.get("shortlink_redirects_total").counter().count());
        assertEquals(cacheMissesBefore + 1.0, meterRegistry.get("shortlink_cache_misses_total").counter().count());
        assertEquals(droppedEventsBefore + 1.0, meterRegistry.get("shortlink_click_events_dropped_total").counter().count());
    }

    @TestConfiguration
    static class FailingRedisTemplateConfiguration {

        @Bean
        @Primary
        @SuppressWarnings("unchecked")
        StringRedisTemplate failingStringRedisTemplate() {
            StringRedisTemplate stringRedisTemplate = mock(StringRedisTemplate.class);
            HashOperations<String, Object, Object> hashOperations = mock(HashOperations.class);
            StreamOperations<String, Object, Object> streamOperations = mock(StreamOperations.class);

            when(stringRedisTemplate.opsForHash()).thenReturn((HashOperations) hashOperations);
            when(stringRedisTemplate.opsForStream()).thenReturn((StreamOperations) streamOperations);

            RedisConnectionFailureException redisFailure =
                    new RedisConnectionFailureException("Simulated Redis outage");

            when(hashOperations.entries(anyString())).thenThrow(redisFailure);
            doThrow(redisFailure).when(hashOperations).putAll(anyString(), anyMap());
            when(stringRedisTemplate.expire(anyString(), any(Duration.class))).thenThrow(redisFailure);
            when(stringRedisTemplate.delete(anyString())).thenThrow(redisFailure);
            when(streamOperations.add(any())).thenThrow(redisFailure);

            return stringRedisTemplate;
        }
    }
}
