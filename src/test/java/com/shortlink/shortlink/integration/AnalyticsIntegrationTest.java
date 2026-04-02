package com.shortlink.shortlink.integration;

import com.shortlink.shortlink.model.Url;
import com.shortlink.shortlink.model.UrlDailyStat;
import com.shortlink.shortlink.model.UrlDailyStatId;
import com.shortlink.shortlink.repository.UrlDailyStatRepository;
import com.shortlink.shortlink.repository.UrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AnalyticsIntegrationTest extends AbstractIntegrationTest {

    private static final String OWNER_EMAIL = "analytics-owner@example.com";
    private static final String OTHER_EMAIL = "analytics-other@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UrlRepository urlRepository;

    @Autowired
    private UrlDailyStatRepository urlDailyStatRepository;

    private String ownerAccessToken;
    private String otherAccessToken;

    @BeforeEach
    void setUp() {
        urlDailyStatRepository.deleteAll();
        urlRepository.deleteAll();
        ownerAccessToken = issueAccessToken(OWNER_EMAIL, "Analytics Owner");
        otherAccessToken = issueAccessToken(OTHER_EMAIL, "Analytics Other");
    }

    @Test
    void shouldReturnAnalyticsForOwner() throws Exception {
        mockMvc.perform(post("/api/v1/urls")
                        .header("Authorization", bearer(ownerAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "originalUrl": "https://example.com/report",
                                  "customAlias": "report-link"
                                }
                                """))
                .andExpect(status().isCreated());

        Url url = urlRepository.findByShortCodeAndIsActiveTrue("report-link").orElseThrow();

        urlDailyStatRepository.save(dailyStat(url.getId(), LocalDate.of(2026, 3, 24), 87L, 60L));
        urlDailyStatRepository.save(dailyStat(url.getId(), LocalDate.of(2026, 3, 25), 142L, 103L));

        mockMvc.perform(get("/api/v1/urls/report-link/analytics")
                        .header("Authorization", bearer(ownerAccessToken))
                        .param("from", "2026-03-24")
                        .param("to", "2026-03-25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortCode").value("report-link"))
                .andExpect(jsonPath("$.totalClicks").value(229))
                .andExpect(jsonPath("$.uniqueClicks").value(163))
                .andExpect(jsonPath("$.clicksByDate.length()").value(2))
                .andExpect(jsonPath("$.clicksByDate[0].date").value("2026-03-24"))
                .andExpect(jsonPath("$.clicksByDate[0].clicks").value(87))
                .andExpect(jsonPath("$.clicksByDate[1].date").value("2026-03-25"))
                .andExpect(jsonPath("$.clicksByDate[1].clicks").value(142));
    }

    @Test
    void shouldHideAnalyticsForAnotherUsersUrl() throws Exception {
        mockMvc.perform(post("/api/v1/urls")
                        .header("Authorization", bearer(ownerAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "originalUrl": "https://example.com/private-report",
                                  "customAlias": "private-report"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/urls/private-report/analytics")
                        .header("Authorization", bearer(otherAccessToken))
                        .param("from", "2026-03-24")
                        .param("to", "2026-03-25"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    private UrlDailyStat dailyStat(java.util.UUID urlId, LocalDate date, long clickCount, long uniqueCount) {
        UrlDailyStat dailyStat = new UrlDailyStat();
        dailyStat.setId(new UrlDailyStatId(urlId, date));
        dailyStat.setClickCount(clickCount);
        dailyStat.setUniqueCount(uniqueCount);
        return dailyStat;
    }
}
