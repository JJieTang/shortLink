package com.shortlink.shortlink.repository;

import com.shortlink.shortlink.model.UrlDailyStat;
import com.shortlink.shortlink.model.UrlDailyStatId;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface UrlDailyStatRepository extends JpaRepository<UrlDailyStat, UrlDailyStatId>, UrlDailyStatBatchRepository {

    List<UrlDailyStat> findByIdUrlIdOrderByIdStatDateDesc(UUID urlId);

    @Modifying
    @Query(value = """
            INSERT INTO url_daily_stats (url_id, stat_date, click_count, unique_count)
            VALUES (:urlId, :statDate, :clickCount, :uniqueCount)
            ON CONFLICT (url_id, stat_date)
            DO UPDATE SET click_count = url_daily_stats.click_count + EXCLUDED.click_count,
                          unique_count = url_daily_stats.unique_count + EXCLUDED.unique_count
            """, nativeQuery = true)
    void upsertDailyCounts(
            @Param("urlId") UUID urlId,
            @Param("statDate") LocalDate statDate,
            @Param("clickCount") long clickCount,
            @Param("uniqueCount") long uniqueCount
    );
}
