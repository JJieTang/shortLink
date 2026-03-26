CREATE TABLE url_daily_stats (
    id UUID NOT NULL REFERENCES urls(id) ON DELETE CASCADE,
    stat_date DATE NOT NULL,
    click_count BIGINT NOT NULL DEFAULT 0,
    unique_count BIGINT NOT NULL DEFAULT 0
)