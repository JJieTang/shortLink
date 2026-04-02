package com.shortlink.shortlink.service;

import com.shortlink.shortlink.dto.AnalyticsResponse;
import com.shortlink.shortlink.exception.InvalidRequestException;
import com.shortlink.shortlink.exception.ResourceNotFoundException;
import com.shortlink.shortlink.model.Url;
import com.shortlink.shortlink.model.UrlDailyStat;
import com.shortlink.shortlink.repository.UrlDailyStatRepository;
import com.shortlink.shortlink.repository.UrlRepository;
import com.shortlink.shortlink.security.CurrentUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class AnalyticsService {

    private final UrlRepository urlRepository;
    private final UrlDailyStatRepository urlDailyStatRepository;
    private final CurrentUserService currentUserService;

    public AnalyticsService(
            UrlRepository urlRepository,
            UrlDailyStatRepository urlDailyStatRepository,
            CurrentUserService currentUserService) {
        this.urlRepository = urlRepository;
        this.urlDailyStatRepository = urlDailyStatRepository;
        this.currentUserService = currentUserService;
    }

    @Transactional(readOnly = true)
    public AnalyticsResponse getAnalytics(String shortCode, LocalDate from, LocalDate to) {
        validateDateRange(from, to);

        Url url = urlRepository.findByShortCodeAndUser_IdAndIsActiveTrue(shortCode, currentUserService.getCurrentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Short code not found: " + shortCode));

        List<UrlDailyStat> dailyStats = urlDailyStatRepository
                .findByIdUrlIdAndIdStatDateBetweenOrderByIdStatDateAsc(url.getId(), from, to);

        long totalClicks = dailyStats.stream()
                .mapToLong(UrlDailyStat::getClickCount)
                .sum();

        long uniqueClicks = dailyStats.stream()
                .mapToLong(UrlDailyStat::getUniqueCount)
                .sum();

        List<AnalyticsResponse.DailyClicks> clicksByDate = dailyStats.stream()
                .map(stat -> new AnalyticsResponse.DailyClicks(
                        stat.getId().getStatDate(),
                        stat.getClickCount()
                ))
                .toList();

        return new AnalyticsResponse(
                url.getShortCode(),
                totalClicks,
                uniqueClicks,
                clicksByDate
        );
    }

    private void validateDateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new InvalidRequestException("from and to are required");
        }

        if (from.isAfter(to)) {
            throw new InvalidRequestException("from must be on or before to");
        }
    }
}
