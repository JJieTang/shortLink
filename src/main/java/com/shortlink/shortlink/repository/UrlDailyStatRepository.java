package com.shortlink.shortlink.repository;

import com.shortlink.shortlink.model.UrlDailyStat;
import com.shortlink.shortlink.model.UrlDailyStatId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UrlDailyStatRepository extends JpaRepository<UrlDailyStat, UrlDailyStatId> {

    List<UrlDailyStat> findByIdUrlIdOrderByIdStatDateDesc(UUID urlId);
}
