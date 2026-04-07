import http from 'k6/http';
import { check, fail, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

import {
  createShortUrl,
  createUserCredentials,
  registerAndLogin,
  resolveBaseUrl,
} from './lib/api.js';

const redirectFoundRate = new Rate('redirect_found_rate');
const redirectDuration = new Trend('redirect_duration', true);
const redirectErrors = new Counter('redirect_unexpected_errors');

const vus = Number(__ENV.REDIRECT_VUS || 1000);
const duration = __ENV.REDIRECT_DURATION || '60s';
const p99BudgetMs = Number(__ENV.REDIRECT_P99_MS || 5);
const thinkTimeMs = Number(__ENV.REDIRECT_THINK_TIME_MS || 0);

export const options = {
  vus,
  duration,
  thresholds: {
    redirect_found_rate: ['rate>0.99'],
    redirect_duration: [`p(99)<${p99BudgetMs}`],
    redirect_unexpected_errors: ['count==0'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
  const baseUrl = resolveBaseUrl();
  const credentials = createUserCredentials('redirect-load');
  const tokens = registerAndLogin(baseUrl, credentials);
  const aliasPrefix = `hot-${Date.now()}`;
  const createdLinks = [];

  for (let index = 0; index < 5; index += 1) {
    const shortUrl = createShortUrl(baseUrl, tokens.accessToken, {
      originalUrl: `https://example.com/hotspot/${index}`,
      customAlias: `${aliasPrefix}-${index}`,
    });
    createdLinks.push(shortUrl);
  }

  const hotspot = createdLinks[0];

  for (const link of createdLinks) {
    const warmResponse = http.get(`${baseUrl}/${link.shortCode}`, {
      headers: {
        'X-Forwarded-For': buildClientIp(indexSeedForShortCode(link.shortCode)),
      },
      redirects: 0,
      responseCallback: http.expectedStatuses(302),
      tags: {
        scenario: 'redirect_warmup',
      },
    });

    check(warmResponse, {
      'warmup redirect returns 302': (response) => response.status === 302,
    }) || fail(`Warmup failed for ${link.shortCode}: ${warmResponse.status}`);
  }

  return {
    baseUrl,
    hotspotShortCode: hotspot.shortCode,
    hotspotLocation: hotspot.originalUrl,
  };
}

export default function (data) {
  const response = http.get(`${data.baseUrl}/${data.hotspotShortCode}`, {
    headers: {
      'X-Forwarded-For': buildClientIp(__VU * 100000 + __ITER),
    },
    redirects: 0,
    responseCallback: http.expectedStatuses(302),
    tags: {
      scenario: 'redirect_hotspot',
      short_code: data.hotspotShortCode,
    },
  });

  redirectDuration.add(response.timings.duration);

  const success = check(response, {
    'redirect returns 302': (res) => res.status === 302,
    'redirect points to hotspot target': (res) => res.headers.Location === data.hotspotLocation,
  });

  redirectFoundRate.add(success);
  if (!success) {
    redirectErrors.add(1);
  }

  if (thinkTimeMs > 0) {
    sleep(thinkTimeMs / 1000);
  }
}

function buildClientIp(seed) {
  const normalized = Math.abs(seed);
  const second = Math.floor(normalized / (255 * 255)) % 255;
  const third = Math.floor(normalized / 255) % 255;
  const fourth = (normalized % 254) + 1;
  return `198.${second}.${third}.${fourth}`;
}

function indexSeedForShortCode(shortCode) {
  return Array.from(shortCode).reduce(
    (sum, character) => sum + character.charCodeAt(0),
    0
  );
}
