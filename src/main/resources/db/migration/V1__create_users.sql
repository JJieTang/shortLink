CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    name VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    daily_quota INTEGER NOT NULL DEFAULT 100,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);


-- ==============================================
INSERT INTO users (id, email, password_hash, name, role) VALUES (
    '00000000-0000-0000-0000-000000000001',
    'seed@shortlink.local',
    '$2a$10$dXJ3SW6G7P50lGmMQoeEhOWHB7gIV56v3neLxP5PqSKJfqNPO4qLi',
    'Seed User',
    'USER'
);