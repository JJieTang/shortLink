package com.shortlink.shortlink.service;

import com.shortlink.shortlink.dto.AnalyticsResponse;
import com.shortlink.shortlink.exception.InvalidRequestException;
import com.shortlink.shortlink.exception.ResourceNotFoundException;
import com.shortlink.shortlink.model.Url;
import com.shortlink.shortlink.model.UrlDailyStat;
import com.shortlink.shortlink.model.UrlDailyStatId;
import com.shortlink.shortlink.repository.UrlDailyStatRepository;
import com.shortlink.shortlink.repository.UrlRepository;
import com.shortlink.shortlink.security.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalyticsServiceTest {

    private UrlRepository urlRepository;
    private UrlDailyStatRepository urlDailyStatRepository;
    private CurrentUserService currentUserService;
    private AnalyticsService analyticsService;

    @BeforeEach
    void setUp() {
        urlRepository = mock(UrlRepository.class);
        urlDailyStatRepository = mock(UrlDailyStatRepository.class);
        currentUserService = mock(CurrentUserService.class);
        analyticsService = new AnalyticsService(urlRepository, urlDailyStatRepository, currentUserService);
    }

    @Test
    void shouldReturnAnalyticsForCurrentUser() {
        UUID currentUserId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        UUID urlId = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");
        LocalDate from = LocalDate.of(2026, 3, 24);
        LocalDate to = LocalDate.of(2026, 3, 25);

        Url url = new Url();
        url.setId(urlId);
        url.setShortCode("aB3xK7");
        url.setTotalClicks(1542L);

        UrlDailyStat firstDay = dailyStat(urlId, LocalDate.of(2026, 3, 24), 87L, 60L);
        UrlDailyStat secondDay = dailyStat(urlId, LocalDate.of(2026, 3, 25), 142L, 103L);

        when(currentUserService.getCurrentUserId()).thenReturn(currentUserId);
        when(urlRepository.findByShortCodeAndUser_IdAndIsActiveTrue("aB3xK7", currentUserId)).thenReturn(Optional.of(url));
        when(urlDailyStatRepository.findByIdUrlIdAndIdStatDateBetweenOrderByIdStatDateAsc(urlId, from, to))
                .thenReturn(List.of(firstDay, secondDay));

        AnalyticsResponse response = analyticsService.getAnalytics("aB3xK7", from, to);

        assertEquals("aB3xK7", response.shortCode());
        assertEquals(1542L, response.totalClicks());
        assertEquals(229L, response.periodClicks());
        assertEquals(163L, response.uniqueClicks());
        assertEquals(2, response.clicksByDate().size());
        assertEquals(LocalDate.of(2026, 3, 24), response.clicksByDate().get(0).date());
        assertEquals(87L, response.clicksByDate().get(0).clicks());
        assertEquals(LocalDate.of(2026, 3, 25), response.clicksByDate().get(1).date());
        assertEquals(142L, response.clicksByDate().get(1).clicks());
    }

    @Test
    void shouldRejectInvalidDateRange() {
        assertThrows(
                InvalidRequestException.class,
                () -> analyticsService.getAnalytics(
                        "aB3xK7",
                        LocalDate.of(2026, 3, 26),
                        LocalDate.of(2026, 3, 25)
                )
        );
    }

    @Test
    void shouldHideAnalyticsOfAnotherUsersUrl() {
        UUID currentUserId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        LocalDate from = LocalDate.of(2026, 3, 24);
        LocalDate to = LocalDate.of(2026, 3, 25);

        when(currentUserService.getCurrentUserId()).thenReturn(currentUserId);
        when(urlRepository.findByShortCodeAndUser_IdAndIsActiveTrue("private-link", currentUserId))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> analyticsService.getAnalytics("private-link", from, to)
        );

        verify(urlRepository).findByShortCodeAndUser_IdAndIsActiveTrue("private-link", currentUserId);
    }

    private UrlDailyStat dailyStat(UUID urlId, LocalDate statDate, long clickCount, long uniqueCount) {
        UrlDailyStat dailyStat = new UrlDailyStat();
        dailyStat.setId(new UrlDailyStatId(urlId, statDate));
        dailyStat.setClickCount(clickCount);
        dailyStat.setUniqueCount(uniqueCount);
        return dailyStat;
    }
}
