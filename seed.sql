-- Demo seed for local reviewer flows.
-- Login credentials:
--   email: demo@shortlink.local
--   password: SecurePass1
-- BCrypt generated with:
--   htpasswd -bnBC 10 "" SecurePass1 | tr -d ':\n'

BEGIN;

DELETE FROM users
WHERE email = 'demo@shortlink.local';

INSERT INTO users (
    id,
    email,
    password_hash,
    name,
    role,
    daily_quota,
    created_at,
    updated_at
) VALUES (
    '11111111-1111-1111-1111-111111111111',
    'demo@shortlink.local',
    '$2y$10$rLgZmxISE.PJb0XSPpLwfeCuOk.T1mz3BNQ/XoinMKfvDEvxjPRSS',
    'Demo User',
    'USER',
    100,
    NOW(),
    NOW()
);

INSERT INTO urls (
    id,
    short_code,
    original_url,
    user_id,
    total_clicks,
    is_active,
    expires_at,
    created_at,
    updated_at
) VALUES
(
    '22222222-2222-2222-2222-222222222222',
    'demo-home',
    'https://example.com/welcome',
    '11111111-1111-1111-1111-111111111111',
    0,
    true,
    NULL,
    NOW(),
    NOW()
),
(
    '33333333-3333-3333-3333-333333333333',
    'launch-day',
    'https://example.com/launch-day',
    '11111111-1111-1111-1111-111111111111',
    0,
    true,
    NULL,
    NOW(),
    NOW()
),
(
    '44444444-4444-4444-4444-444444444444',
    'future-news',
    'https://example.com/future-news',
    '11111111-1111-1111-1111-111111111111',
    0,
    true,
    '2099-12-31T23:59:59Z',
    NOW(),
    NOW()
);

COMMIT;
