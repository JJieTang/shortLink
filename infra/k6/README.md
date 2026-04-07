# k6 Benchmarks

This directory holds self-contained load tests for the Phase 6 acceptance path.

## Files

- `redirect-load.js`: hotspot redirect benchmark for `GET /{shortCode}`
- `management-load.js`: authenticated management benchmark for `/api/v1/urls`
- `lib/api.js`: shared auth and URL helpers used by both scripts

## Why these scripts are self-contained

Each script registers a fresh user, logs in, and creates the data it needs.
That makes the benchmark reproducible and avoids hidden dependencies on `seed.sql`.

## Commands

Run the redirect hotspot benchmark:

```bash
k6 run infra/k6/redirect-load.js
```

Run the management benchmark:

```bash
k6 run infra/k6/management-load.js
```

Override the target host:

```bash
BASE_URL=http://localhost:8080 k6 run infra/k6/redirect-load.js
```

Tune redirect pressure:

```bash
REDIRECT_VUS=1000 REDIRECT_DURATION=60s REDIRECT_P99_MS=5 k6 run infra/k6/redirect-load.js
```

Tune management pressure:

```bash
MANAGEMENT_VUS=80 MANAGEMENT_DURATION=45s k6 run infra/k6/management-load.js
```

## Reading the output

- `redirect-load.js` checks that redirects keep returning `302` and records a dedicated `redirect_duration` trend so setup/login requests do not pollute the latency number.
- `management-load.js` treats `429` as an expected outcome and reports whether rate limiting was actually triggered under load.

## Environment variables

- `BASE_URL`: API base URL, default `http://localhost:8080`
- `K6_PASSWORD`: password used for generated benchmark users, default `ShortLink9`
- `REDIRECT_VUS`, `REDIRECT_DURATION`, `REDIRECT_P99_MS`, `REDIRECT_THINK_TIME_MS`
- `MANAGEMENT_VUS`, `MANAGEMENT_DURATION`, `MANAGEMENT_THINK_TIME_MS`

## Important note about the `p99 < 5ms` target

The script defaults to the design-doc target, but that number is aggressive outside a warm local environment.
If you are running through Docker networking, expect higher latency and capture the environment details when you report the result.
