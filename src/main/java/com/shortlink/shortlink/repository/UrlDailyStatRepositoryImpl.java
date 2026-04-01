package com.shortlink.shortlink.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;

public class UrlDailyStatRepositoryImpl implements UrlDailyStatBatchRepository {

    private static final String UPSERT_DAILY_COUNTS_SQL = """
            INSERT INTO url_daily_stats (url_id, stat_date, click_count, unique_count)
            VALUES (:urlId, :statDate, :clickCount, :uniqueCount)
            ON CONFLICT (url_id, stat_date)
            DO UPDATE SET click_count = url_daily_stats.click_count + EXCLUDED.click_count,
                          unique_count = url_daily_stats.unique_count + EXCLUDED.unique_count
            """;

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public UrlDailyStatRepositoryImpl(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    @Override
    public void upsertDailyCountsBatch(List<DailyCountUpdate> updates) {
        if (updates == null || updates.isEmpty()) {
            return;
        }

        MapSqlParameterSource[] batchParameters = updates.stream()
                .map(update -> new MapSqlParameterSource()
                        .addValue("urlId", update.urlId())
                        .addValue("statDate", update.statDate())
                        .addValue("clickCount", update.clickCount())
                        .addValue("uniqueCount", update.uniqueCount()))
                .toArray(MapSqlParameterSource[]::new);

        namedParameterJdbcTemplate.batchUpdate(UPSERT_DAILY_COUNTS_SQL, batchParameters);
    }
}
