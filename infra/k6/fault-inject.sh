#!/usr/bin/env bash

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
FAILOVER_VUS="${FAILOVER_VUS:-20}"
FAILOVER_DURATION="${FAILOVER_DURATION:-45s}"
FAIL_AT_SECONDS="${FAIL_AT_SECONDS:-10}"
RECOVER_AT_SECONDS="${RECOVER_AT_SECONDS:-25}"
FAILOVER_THINK_TIME_MS="${FAILOVER_THINK_TIME_MS:-0}"
K6_SUMMARY_EXPORT="${K6_SUMMARY_EXPORT:-}"
K6_BIN="${K6_BIN:-k6}"
DOCKER_COMPOSE_BIN="${DOCKER_COMPOSE_BIN:-docker compose}"

log() {
  printf '[fault-inject] %s\n' "$1"
}

cleanup() {
  log "Ensuring Redis is running"
  ${DOCKER_COMPOSE_BIN} start redis >/dev/null 2>&1 || true
}

trap cleanup EXIT

if (( RECOVER_AT_SECONDS <= FAIL_AT_SECONDS )); then
  echo "RECOVER_AT_SECONDS must be greater than FAIL_AT_SECONDS" >&2
  exit 1
fi

log "Checking API health at ${BASE_URL}/actuator/health"
curl -fsS "${BASE_URL}/actuator/health" >/dev/null

log "Starting redis failover benchmark in the background"
k6_args=(
  run
  infra/k6/redis-failover.js
)

if [[ -n "${K6_SUMMARY_EXPORT}" ]]; then
  k6_args+=(--summary-export "${K6_SUMMARY_EXPORT}")
fi

BASE_URL="${BASE_URL}" \
FAILOVER_VUS="${FAILOVER_VUS}" \
FAILOVER_DURATION="${FAILOVER_DURATION}" \
FAIL_AT_SECONDS="${FAIL_AT_SECONDS}" \
RECOVER_AT_SECONDS="${RECOVER_AT_SECONDS}" \
FAILOVER_THINK_TIME_MS="${FAILOVER_THINK_TIME_MS}" \
"${K6_BIN}" "${k6_args[@]}" &

k6_pid=$!

sleep "${FAIL_AT_SECONDS}"
log "Stopping Redis to trigger degraded mode"
${DOCKER_COMPOSE_BIN} stop redis

sleep "$(( RECOVER_AT_SECONDS - FAIL_AT_SECONDS ))"
log "Starting Redis to observe recovery"
${DOCKER_COMPOSE_BIN} start redis

log "Waiting for the benchmark to finish"
wait "${k6_pid}"
