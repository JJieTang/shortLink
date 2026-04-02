package com.shortlink.shortlink.integration;

import com.shortlink.shortlink.model.Url;
import com.shortlink.shortlink.model.UrlDailyStat;
import com.shortlink.shortlink.repository.ClickEventRepository;
import com.shortlink.shortlink.repository.UrlDailyStatRepository;
import com.shortlink.shortlink.repository.UrlRepository;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ClickPipelineIntegrationTest extends AbstractIntegrationTest {

    private static final String OWNER_EMAIL = "pipeline-owner@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UrlRepository urlRepository;

    @Autowired
    private ClickEventRepository clickEventRepository;

    @Autowired
    private UrlDailyStatRepository urlDailyStatRepository;

    private String ownerAccessToken;

    @BeforeEach
    void setUp() {
        clickEventRepository.deleteAll();
        urlDailyStatRepository.deleteAll();
        urlRepository.deleteAll();
        ownerAccessToken = issueAccessToken(OWNER_EMAIL, "Pipeline Owner");
    }

    @Test
    void shouldPersistClickEventsAndAggregateDailyStatsFromRedisStream() throws Exception {
        mockMvc.perform(post("/api/v1/urls")
                        .header("Authorization", bearer(ownerAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "originalUrl": "https://example.com/analytics",
                                  "customAlias": "analytics-link"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/analytics-link")
                        .header("X-Forwarded-For", "203.0.113.7")
                        .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36")
                        .header("Referer", "https://ref.example.com/first"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/analytics"));

        mockMvc.perform(get("/analytics-link")
                        .header("X-Forwarded-For", "203.0.113.7")
                        .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36")
                        .header("Referer", "https://ref.example.com/second"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/analytics"));

        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    Url url = urlRepository.findByShortCodeAndIsActiveTrue("analytics-link").orElseThrow();
                    List<UrlDailyStat> dailyStats = urlDailyStatRepository.findByIdUrlIdOrderByIdStatDateDesc(url.getId());

                    assertEquals(2L, clickEventRepository.count());
                    assertEquals(2L, url.getTotalClicks());
                    assertFalse(dailyStats.isEmpty());

                    UrlDailyStat latestDailyStat = dailyStats.getFirst();
                    assertNotNull(latestDailyStat.getId());
                    assertEquals(2L, latestDailyStat.getClickCount());
                    assertEquals(1L, latestDailyStat.getUniqueCount());
                });
    }
}
