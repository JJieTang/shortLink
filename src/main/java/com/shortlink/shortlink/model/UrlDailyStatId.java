package com.shortlink.shortlink.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class UrlDailyStatId implements Serializable {

    @Column(name = "url_id", nullable = false)
    private UUID urlId;

    @Column(name = "stat_date", nullable = false)
    private LocalDate statDate;

    public UrlDailyStatId() {
    }

    public UrlDailyStatId(UUID urlId, LocalDate statDate) {
        this.urlId = urlId;
        this.statDate = statDate;
    }

    public UUID getUrlId() {
        return urlId;
    }

    public void setUrlId(UUID urlId) {
        this.urlId = urlId;
    }

    public LocalDate getStatDate() {
        return statDate;
    }

    public void setStatDate(LocalDate statDate) {
        this.statDate = statDate;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof UrlDailyStatId that)) {
            return false;
        }
        return Objects.equals(urlId, that.urlId) && Objects.equals(statDate, that.statDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(urlId, statDate);
    }
}
