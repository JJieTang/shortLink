CREATE TABLE click_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    url_id UUID NOT NULL REFERENCES urls(id) ON DELETE CASCADE,
    event_id UUID UNIQUE NOT NULL,
    clicked_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ip_address INET,
    country VARCHAR(2),
    city VARCHAR(100),
    device_type VARCHAR(20),
    os VARCHAR(50),
    browser VARCHAR(50),
    referrer VARCHAR(2048),
    user_agent VARCHAR(512),
    trace_id VARCHAR(64)
);