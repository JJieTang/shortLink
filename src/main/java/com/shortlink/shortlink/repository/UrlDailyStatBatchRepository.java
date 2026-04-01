package com.shortlink.shortlink.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface UrlDailyStatBatchRepository {

    void upsertDailyCountsBatch(List<DailyCountUpdate> updates);

    record DailyCountUpdate(
            UUID urlId,
            LocalDate statDate,
            long clickCount,
            long uniqueCount) {
    }
}
