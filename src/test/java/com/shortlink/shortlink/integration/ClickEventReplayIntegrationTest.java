package com.shortlink.shortlink.integration;

import com.shortlink.shortlink.model.Url;
import com.shortlink.shortlink.model.UrlDailyStat;
import com.shortlink.shortlink.repository.ClickEventRepository;
import com.shortlink.shortlink.repository.UrlDailyStatRepository;
import com.shortlink.shortlink.repository.UrlRepository;
import com.shortlink.shortlink.service.ClickEventReplayService;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ClickEventReplayIntegrationTest extends AbstractIntegrationTest {

    private static final String DLQ_STREAM_KEY = "click-events-dlq";
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ClickEventReplayService clickEventReplayService;

    @Autowired
    private UrlRepository urlRepository;

    @Autowired
    private ClickEventRepository clickEventRepository;

    @Autowired
    private UrlDailyStatRepository urlDailyStatRepository;

    @BeforeEach
    void setUp() {
        clickEventRepository.deleteAll();
        urlDailyStatRepository.deleteAll();
        urlRepository.deleteAll();
        stringRedisTemplate.delete(DLQ_STREAM_KEY);
    }

    @Test
    void shouldReplayDlqMessageBackToMainStreamAndProcessIt() throws Exception {
        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "originalUrl": "https://example.com/replay",
                                  "customAlias": "replay-link"
                                }
                                """))
                .andExpect(status().isCreated());

        Url url = urlRepository.findByShortCodeAndIsActiveTrue("replay-link").orElseThrow();

        Map<String, String> dlqPayload = new LinkedHashMap<>();
        dlqPayload.put("eventId", UUID.randomUUID().toString());
        dlqPayload.put("urlId", url.getId().toString());
        dlqPayload.put("shortCode", "replay-link");
        dlqPayload.put("clickedAt", Instant.parse("2026-03-31T12:00:00Z").toString());
        dlqPayload.put("ipAddress", "203.0.113.88");
        dlqPayload.put("referrer", "https://ref.example.com/replayed");
        dlqPayload.put("userAgent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36");
        dlqPayload.put("traceId", UUID.randomUUID().toString());
        dlqPayload.put("retryCount", "3");
        dlqPayload.put("originalMessageId", "1711111111111-0");
        dlqPayload.put("failedAt", Instant.now().toString());
        dlqPayload.put("errorType", "IllegalStateException");
        dlqPayload.put("errorMessage", "Replay me");

        stringRedisTemplate.opsForStream().add(DLQ_STREAM_KEY, dlqPayload);

        List<MapRecord<String, Object, Object>> dlqMessagesBeforeReplay =
                stringRedisTemplate.opsForStream().range(DLQ_STREAM_KEY, Range.unbounded());
        assertEquals(1, dlqMessagesBeforeReplay.size());

        clickEventReplayService.replay(dlqMessagesBeforeReplay.getFirst());

        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    Url refreshedUrl = urlRepository.findByShortCodeAndIsActiveTrue("replay-link").orElseThrow();
                    List<MapRecord<String, Object, Object>> remainingDlqMessages =
                            stringRedisTemplate.opsForStream().range(DLQ_STREAM_KEY, Range.unbounded());
                    List<UrlDailyStat> dailyStats =
                            urlDailyStatRepository.findByIdUrlIdOrderByIdStatDateDesc(url.getId());

                    assertEquals(1L, clickEventRepository.count());
                    assertEquals(1L, refreshedUrl.getTotalClicks());
                    assertTrue(remainingDlqMessages == null || remainingDlqMessages.isEmpty());
                    assertFalse(dailyStats.isEmpty());

                    UrlDailyStat latestDailyStat = dailyStats.getFirst();
                    assertNotNull(latestDailyStat.getId());
                    assertEquals(1L, latestDailyStat.getClickCount());
                    assertEquals(1L, latestDailyStat.getUniqueCount());
                });
    }

}
