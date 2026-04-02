package com.shortlink.shortlink.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// Spring Data picks up this custom repository fragment implementation by the `Impl` suffix.
public class ClickEventRepositoryImpl implements ClickEventBatchRepository {

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public ClickEventRepositoryImpl(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    @Override
    public List<ExistingUniqueVisitor> findExistingUniqueVisitors(List<UniqueVisitorCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        MapSqlParameterSource parameters = new MapSqlParameterSource();
        List<String> valueRows = new ArrayList<>(candidates.size());

        for (int i = 0; i < candidates.size(); i++) {
            UniqueVisitorCandidate candidate = candidates.get(i);
            valueRows.add("""
                    (:urlId%s, :ipAddress%s, :statDate%s, :startInclusive%s, :endExclusive%s)
                    """.formatted(i, i, i, i, i).trim());
            parameters.addValue("urlId" + i, candidate.urlId());
            parameters.addValue("ipAddress" + i, candidate.ipAddress());
            parameters.addValue("statDate" + i, candidate.statDate());
            parameters.addValue("startInclusive" + i, candidate.startInclusive());
            parameters.addValue("endExclusive" + i, candidate.endExclusive());
        }

        String sql = """
                WITH candidates (url_id, ip_address, stat_date, start_inclusive, end_exclusive) AS (
                    VALUES %s
                )
                SELECT DISTINCT c.url_id, c.ip_address, c.stat_date
                FROM candidates c
                JOIN click_events ce
                  ON ce.url_id = c.url_id
                 AND ce.ip_address = c.ip_address
                 AND ce.clicked_at >= c.start_inclusive
                 AND ce.clicked_at < c.end_exclusive
                """.formatted(String.join(", ", valueRows));

        return namedParameterJdbcTemplate.query(
                sql,
                parameters,
                (resultSet, rowNum) -> new ExistingUniqueVisitor(
                        resultSet.getObject("url_id", UUID.class),
                        resultSet.getObject("stat_date", java.time.LocalDate.class),
                        resultSet.getString("ip_address")
                )
        );
    }
}
