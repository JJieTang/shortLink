package com.shortlink.shortlink.integration;

import com.shortlink.shortlink.event.ClickEventMessage;
import com.shortlink.shortlink.repository.ClickEventRepository;
import com.shortlink.shortlink.service.ClickEventConsumer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ClickEventDlqIntegrationTest extends AbstractIntegrationTest {

    private static final String DLQ_STREAM_KEY = "click-events-dlq";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ClickEventRepository clickEventRepository;

    @BeforeEach
    void setUp() {
        clickEventRepository.deleteAll();
        stringRedisTemplate.delete(DLQ_STREAM_KEY);
    }

    @Test
    void shouldMoveMessageToDlqAfterRetryLimit() throws Exception {
        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "originalUrl": "https://example.com/dlq",
                                  "customAlias": "dlq-link"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/dlq-link")
                        .header("X-Forwarded-For", "203.0.113.77"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/dlq"));

        Awaitility.await()
                .atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> {
                    List<MapRecord<String, Object, Object>> dlqMessages =
                            stringRedisTemplate.opsForStream().range(DLQ_STREAM_KEY, Range.unbounded());

                    assertEquals(0L, clickEventRepository.count());
                    assertEquals(1, dlqMessages.size());

                    MapRecord<String, Object, Object> dlqMessage = dlqMessages.getFirst();
                    assertEquals("dlq-link", dlqMessage.getValue().get("shortCode"));
                    assertEquals("3", dlqMessage.getValue().get("retryCount"));
                    assertEquals("IllegalStateException", dlqMessage.getValue().get("errorType"));
                    assertTrue(((String) dlqMessage.getValue().get("errorMessage")).contains("Intentional test failure"));
                    assertFalse(((String) dlqMessage.getValue().get("originalMessageId")).isBlank());
                });
    }

    @TestConfiguration
    static class FailingConsumerConfiguration {

        @Bean
        @Primary
        ClickEventConsumer failingClickEventConsumer() {
            return new ClickEventConsumer(null, null, null, null, null) {
                @Override
                public void consume(ClickEventMessage eventMessage) {
                    throw new IllegalStateException("Intentional test failure");
                }
            };
        }
    }
}
