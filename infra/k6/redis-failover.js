import exec from 'k6/execution';
import http from 'k6/http';
import { check, fail, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

import {
  createShortUrl,
  createUserCredentials,
  registerAndLogin,
  resolveBaseUrl,
} from './lib/api.js';

const outageRedirectSuccessRate = new Rate('redis_outage_redirect_success_rate');
const recoveryRedirectSuccessRate = new Rate('redis_recovery_redirect_success_rate');
const outageRedirectDuration = new Trend('redis_outage_redirect_duration', true);
const recoveryRedirectDuration = new Trend('redis_recovery_redirect_duration', true);
const failoverUnexpectedErrors = new Counter('redis_failover_unexpected_errors');

const vus = Number(__ENV.FAILOVER_VUS || 20);
const duration = __ENV.FAILOVER_DURATION || '45s';
const failAtSeconds = Number(__ENV.FAIL_AT_SECONDS || 10);
const recoverAtSeconds = Number(__ENV.RECOVER_AT_SECONDS || 25);
const thinkTimeMs = Number(__ENV.FAILOVER_THINK_TIME_MS || 0);

export const options = {
  vus,
  duration,
  thresholds: {
    redis_outage_redirect_success_rate: ['rate>0.99'],
    redis_recovery_redirect_success_rate: ['rate>0.99'],
    redis_failover_unexpected_errors: ['count==0'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
  if (recoverAtSeconds <= failAtSeconds) {
    fail('RECOVER_AT_SECONDS must be greater than FAIL_AT_SECONDS');
  }

  const baseUrl = resolveBaseUrl();
  const credentials = createUserCredentials('redis-failover');
  const tokens = registerAndLogin(baseUrl, credentials);
  const alias = `failover-${Date.now()}`;

  const hotspot = createShortUrl(baseUrl, tokens.accessToken, {
    originalUrl: 'https://example.com/failover/hotspot',
    customAlias: alias,
  });

  const warmResponse = http.get(`${baseUrl}/${hotspot.shortCode}`, {
    headers: {
      'X-Forwarded-For': buildClientIp(1),
    },
    redirects: 0,
    responseCallback: http.expectedStatuses(302),
    tags: {
      scenario: 'redis_failover_warmup',
    },
  });

  check(warmResponse, {
    'warmup redirect returns 302': (response) => response.status === 302,
  }) || fail(`Warmup failed for ${hotspot.shortCode}: ${warmResponse.status}`);

  return {
    baseUrl,
    hotspotShortCode: hotspot.shortCode,
    hotspotLocation: hotspot.originalUrl,
  };
}

export default function (data) {
  const elapsedSeconds = exec.instance.currentTestRunDuration / 1000;
  const phase = resolvePhase(elapsedSeconds);

  const response = http.get(`${data.baseUrl}/${data.hotspotShortCode}`, {
    headers: {
      'X-Forwarded-For': buildClientIp(__VU * 100000 + __ITER),
    },
    redirects: 0,
    responseCallback: http.expectedStatuses(302),
    tags: {
      scenario: 'redis_failover',
      phase,
    },
  });

  const success = check(response, {
    [`${phase} redirect returns 302`]: (res) => res.status === 302,
    [`${phase} redirect keeps target stable`]: (res) => res.headers.Location === data.hotspotLocation,
  });

  if (phase === 'outage') {
    outageRedirectSuccessRate.add(success);
    outageRedirectDuration.add(response.timings.duration);
  } else if (phase === 'recovery') {
    recoveryRedirectSuccessRate.add(success);
    recoveryRedirectDuration.add(response.timings.duration);
  }

  if (!success) {
    failoverUnexpectedErrors.add(1);
  }

  if (thinkTimeMs > 0) {
    sleep(thinkTimeMs / 1000);
  }
}

function resolvePhase(elapsedSeconds) {
  if (elapsedSeconds < failAtSeconds) {
    return 'steady';
  }
  if (elapsedSeconds < recoverAtSeconds) {
    return 'outage';
  }
  return 'recovery';
}

function buildClientIp(seed) {
  const normalized = Math.abs(seed);
  const second = Math.floor(normalized / (255 * 255)) % 255;
  const third = Math.floor(normalized / 255) % 255;
  const fourth = (normalized % 254) + 1;
  return `198.${second}.${third}.${fourth}`;
}
