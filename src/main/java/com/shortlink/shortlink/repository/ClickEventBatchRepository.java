package com.shortlink.shortlink.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ClickEventBatchRepository {

    List<ExistingUniqueVisitor> findExistingUniqueVisitors(List<UniqueVisitorCandidate> candidates);

    record UniqueVisitorCandidate(
            UUID urlId,
            LocalDate statDate,
            String ipAddress,
            Instant startInclusive,
            Instant endExclusive
    ) {
    }

    record ExistingUniqueVisitor(
            UUID urlId,
            LocalDate statDate,
            String ipAddress
    ) {
    }
}
