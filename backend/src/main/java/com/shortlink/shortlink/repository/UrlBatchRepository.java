package com.shortlink.shortlink.repository;

import java.util.List;
import java.util.UUID;

public interface UrlBatchRepository {

    void incrementTotalClicksBatch(List<TotalClickUpdate> updates);

    record TotalClickUpdate(UUID urlId, long delta) {
    }
}
