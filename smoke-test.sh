#!/usr/bin/env bash
set -euo pipefail

if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
  COMPOSE_CMD=(docker compose)
elif command -v docker-compose >/dev/null 2>&1; then
  COMPOSE_CMD=(docker-compose)
else
  echo "docker compose (or docker-compose) is required." >&2
  exit 1
fi

for required_cmd in curl jq; do
  if ! command -v "$required_cmd" >/dev/null 2>&1; then
    echo "Missing required command: $required_cmd" >&2
    exit 1
  fi
done

if [[ -f .env ]]; then
  set -a
  # shellcheck disable=SC1091
  source ./.env
  set +a
fi

APP_PORT="${APP_PORT:-8080}"
POSTGRES_DB="${POSTGRES_DB:-shortlink}"
POSTGRES_USER="${POSTGRES_USER:-shortlink}"
APP_BASE_URL="http://localhost:${APP_PORT}"
TODAY_UTC="$(date -u +%Y-%m-%d)"
SEED_EMAIL="demo@shortlink.local"
SEED_PASSWORD="SecurePass1"
SEED_SHORT_CODE="demo-home"
SEED_REDIRECT_TARGET="https://example.com/welcome"
SMOKE_ALIAS="smoke-$(date +%s)"
SMOKE_TARGET="https://example.com/smoke-check"
ANALYTICS_FROM="${TODAY_UTC}"
ANALYTICS_TO="${TODAY_UTC}"

wait_for_app() {
  echo "Waiting for app health endpoint..."
  for _ in $(seq 1 60); do
    if curl -fsS "${APP_BASE_URL}/actuator/health" >/dev/null 2>&1; then
      echo "App is healthy."
      return 0
    fi
    sleep 2
  done

  echo "App did not become healthy in time." >&2
  return 1
}

seed_database() {
  echo "Loading demo seed data..."
  "${COMPOSE_CMD[@]}" exec -T postgres \
    psql -v ON_ERROR_STOP=1 -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" \
    < seed.sql
}

assert_seed_redirect() {
  echo "Checking seeded public redirect..."
  local headers
  headers="$(curl -sS -D - -o /dev/null "${APP_BASE_URL}/${SEED_SHORT_CODE}")"

  local status
  status="$(printf '%s\n' "${headers}" | sed -n 's/^HTTP\/[^ ]* \([0-9][0-9][0-9]\).*/\1/p' | tail -n 1)"
  local location
  location="$(printf '%s\n' "${headers}" | sed -n 's/^[Ll]ocation: //p' | tr -d '\r' | tail -n 1)"

  [[ "${status}" == "302" ]] || {
    echo "Expected seeded redirect to return 302, got: ${status}" >&2
    return 1
  }
  [[ "${location}" == "${SEED_REDIRECT_TARGET}" ]] || {
    echo "Expected seeded redirect target ${SEED_REDIRECT_TARGET}, got: ${location}" >&2
    return 1
  }
}

login_and_get_token() {
  echo "Logging in with seeded demo user..." >&2
  local response
  response="$(curl -fsS \
    -X POST "${APP_BASE_URL}/api/v1/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"${SEED_EMAIL}\",\"password\":\"${SEED_PASSWORD}\"}")"

  printf '%s' "${response}" | jq -r '.accessToken'
}

create_smoke_url() {
  local access_token="$1"
  echo "Creating a new smoke-test short URL..."
  local response
  response="$(curl -fsS \
    -X POST "${APP_BASE_URL}/api/v1/urls" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer ${access_token}" \
    -d "{\"originalUrl\":\"${SMOKE_TARGET}\",\"customAlias\":\"${SMOKE_ALIAS}\"}")"

  local short_code
  short_code="$(printf '%s' "${response}" | jq -r '.shortCode')"
  [[ "${short_code}" == "${SMOKE_ALIAS}" ]] || {
    echo "Expected created short code ${SMOKE_ALIAS}, got: ${short_code}" >&2
    return 1
  }
}

hit_redirect_and_assert() {
  local short_code="$1"
  local forwarded_for="$2"
  local headers

  headers="$(curl -sS -D - -o /dev/null \
    -H "X-Forwarded-For: ${forwarded_for}" \
    -H "User-Agent: smoke-test-script/1.0" \
    "${APP_BASE_URL}/${short_code}")"

  local status
  status="$(printf '%s\n' "${headers}" | sed -n 's/^HTTP\/[^ ]* \([0-9][0-9][0-9]\).*/\1/p' | tail -n 1)"
  local location
  location="$(printf '%s\n' "${headers}" | sed -n 's/^[Ll]ocation: //p' | tr -d '\r' | tail -n 1)"

  [[ "${status}" == "302" ]] || {
    echo "Expected redirect for ${short_code} to return 302, got: ${status}" >&2
    return 1
  }
  [[ "${location}" == "${SMOKE_TARGET}" ]] || {
    echo "Expected redirect target ${SMOKE_TARGET}, got: ${location}" >&2
    return 1
  }
}

poll_analytics() {
  local access_token="$1"
  local short_code="$2"

  echo "Polling analytics until async click processing completes..."
  for _ in $(seq 1 30); do
    local response
    response="$(curl -fsS \
      -H "Authorization: Bearer ${access_token}" \
      "${APP_BASE_URL}/api/v1/urls/${short_code}/analytics?from=${ANALYTICS_FROM}&to=${ANALYTICS_TO}")"

    local period_clicks
    period_clicks="$(printf '%s' "${response}" | jq -r '.periodClicks')"
    local unique_clicks
    unique_clicks="$(printf '%s' "${response}" | jq -r '.uniqueClicks')"
    local total_clicks
    total_clicks="$(printf '%s' "${response}" | jq -r '.totalClicks')"

    if [[ "${period_clicks}" -ge 2 && "${unique_clicks}" -ge 2 && "${total_clicks}" -ge 2 ]]; then
      echo "Analytics ready: total=${total_clicks}, period=${period_clicks}, unique=${unique_clicks}"
      return 0
    fi

    sleep 1
  done

  echo "Analytics did not reflect the redirect traffic in time." >&2
  return 1
}

main() {
  wait_for_app
  seed_database
  assert_seed_redirect

  local access_token
  access_token="$(login_and_get_token)"
  [[ -n "${access_token}" && "${access_token}" != "null" ]] || {
    echo "Login did not return an access token." >&2
    exit 1
  }

  create_smoke_url "${access_token}"
  hit_redirect_and_assert "${SMOKE_ALIAS}" "198.51.100.10"
  hit_redirect_and_assert "${SMOKE_ALIAS}" "198.51.100.11"
  poll_analytics "${access_token}" "${SMOKE_ALIAS}"

  echo "PASS: login, create URL, redirect, and analytics flow all succeeded."
}

main "$@"
