package com.shortlink.shortlink.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "url_daily_stats")
public class UrlDailyStat {

    @EmbeddedId
    private UrlDailyStatId id;

    @Column(name = "click_count", nullable = false)
    private Long clickCount = 0L;

    @Column(name = "unique_count", nullable = false)
    private Long uniqueCount = 0L;

    public UrlDailyStat() {
    }

    public UrlDailyStatId getId() {
        return id;
    }

    public void setId(UrlDailyStatId id) {
        this.id = id;
    }

    public Long getClickCount() {
        return clickCount;
    }

    public void setClickCount(Long clickCount) {
        this.clickCount = clickCount;
    }

    public Long getUniqueCount() {
        return uniqueCount;
    }

    public void setUniqueCount(Long uniqueCount) {
        this.uniqueCount = uniqueCount;
    }
}
