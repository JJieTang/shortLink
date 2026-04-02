-- Phase 1 introduced a fixed seed user for temporary ownership wiring.
-- Phase 3 uses real authenticated users, so we remove the bootstrap record.
DELETE FROM users
WHERE id = '00000000-0000-0000-0000-000000000001'
  AND email = 'seed@shortlink.local';
