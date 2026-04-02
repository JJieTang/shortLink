package com.shortlink.shortlink.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;

// Spring Data picks up this custom repository fragment implementation by the `Impl` suffix.
public class UrlRepositoryImpl implements UrlBatchRepository {

    private static final String INCREMENT_TOTAL_CLICKS_SQL = """
            UPDATE urls
            SET total_clicks = total_clicks + :delta
            WHERE id = :urlId
            """;

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public UrlRepositoryImpl(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    @Override
    public void incrementTotalClicksBatch(List<TotalClickUpdate> updates) {
        if (updates == null || updates.isEmpty()) {
            return;
        }

        MapSqlParameterSource[] batchParameters = updates.stream()
                .map(update -> new MapSqlParameterSource()
                        .addValue("urlId", update.urlId())
                        .addValue("delta", update.delta()))
                .toArray(MapSqlParameterSource[]::new);

        namedParameterJdbcTemplate.batchUpdate(INCREMENT_TOTAL_CLICKS_SQL, batchParameters);
    }
}
