ALTER TABLE url_daily_stats
    RENAME COLUMN id TO url_id;

ALTER TABLE url_daily_stats
    ALTER COLUMN url_id SET NOT NULL;

ALTER TABLE url_daily_stats
    ALTER COLUMN stat_date SET NOT NULL;

ALTER TABLE url_daily_stats
    ALTER COLUMN click_count SET DEFAULT 0;

ALTER TABLE url_daily_stats
    ALTER COLUMN click_count SET NOT NULL;

ALTER TABLE url_daily_stats
    ALTER COLUMN unique_count SET DEFAULT 0;

ALTER TABLE url_daily_stats
    ALTER COLUMN unique_count SET NOT NULL;

ALTER TABLE url_daily_stats
    ADD CONSTRAINT uk_url_daily_stats_url_id_stat_date
        UNIQUE (url_id, stat_date);

CREATE INDEX idx_url_daily_stats_url_id_stat_date_desc
    ON url_daily_stats (url_id, stat_date DESC);
