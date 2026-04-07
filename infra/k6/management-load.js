import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate } from 'k6/metrics';

import {
  createUserCredentials,
  deleteUrl,
  listUrls,
  registerAndLogin,
  resolveBaseUrl,
} from './lib/api.js';

const managementExpectedOutcome = new Rate('management_expected_outcome');
const rateLimitedRate = new Rate('management_rate_limited_rate');
const rateLimitedCount = new Counter('management_rate_limited_count');

const vus = Number(__ENV.MANAGEMENT_VUS || 80);
const duration = __ENV.MANAGEMENT_DURATION || '45s';
const thinkTimeMs = Number(__ENV.MANAGEMENT_THINK_TIME_MS || 200);

let createdCodes = [];

export const options = {
  vus,
  duration,
  thresholds: {
    management_expected_outcome: ['rate>0.99'],
    management_rate_limited_count: ['count>0'],
  },
  summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
  const baseUrl = resolveBaseUrl();
  const credentials = createUserCredentials('management-load');
  const tokens = registerAndLogin(baseUrl, credentials);

  return {
    baseUrl,
    accessToken: tokens.accessToken,
    aliasPrefix: `mgmt-${Date.now()}`,
  };
}

export default function (data) {
  const operationIndex = __ITER % 3;
  let response;

  if (operationIndex === 0) {
    const alias = `${data.aliasPrefix}-${__VU}-${__ITER}`;
    response = createWithExpectedOutcome(data.baseUrl, data.accessToken, alias);
    if (response.status === 201) {
      createdCodes.push(alias);
    }
  } else if (operationIndex === 1) {
    response = listUrls(data.baseUrl, data.accessToken, {
      responseCallback: http.expectedStatuses(200, 429),
      tags: {
        scenario: 'management_list',
      },
    });
    recordOutcome(response, [200, 429], 'list');
  } else if (createdCodes.length > 0) {
    const shortCode = createdCodes.pop();
    response = deleteUrl(data.baseUrl, data.accessToken, shortCode, {
      responseCallback: http.expectedStatuses(204, 429),
      tags: {
        scenario: 'management_delete',
      },
    });
    recordOutcome(response, [204, 429], 'delete');
  } else {
    response = listUrls(data.baseUrl, data.accessToken, {
      responseCallback: http.expectedStatuses(200, 429),
      tags: {
        scenario: 'management_list',
      },
    });
    recordOutcome(response, [200, 429], 'list-fallback');
  }

  if (thinkTimeMs > 0) {
    sleep(thinkTimeMs / 1000);
  }
}

function createWithExpectedOutcome(baseUrl, accessToken, alias) {
  const response = http.post(
    `${baseUrl}/api/v1/urls`,
    JSON.stringify({
      originalUrl: `https://example.com/management/${alias}`,
      customAlias: alias,
    }),
    {
      headers: {
        'Content-Type': 'application/json',
        Accept: 'application/json',
        Authorization: `Bearer ${accessToken}`,
      },
      responseCallback: http.expectedStatuses(201, 429),
      tags: {
        scenario: 'management_create',
      },
    }
  );

  recordOutcome(response, [201, 429], 'create');
  return response;
}

function recordOutcome(response, expectedStatuses, operation) {
  const expected = expectedStatuses.includes(response.status);
  const wasRateLimited = response.status === 429;

  check(response, {
    [`${operation} returns an expected status`]: () => expected,
  });

  managementExpectedOutcome.add(expected);
  rateLimitedRate.add(wasRateLimited);
  if (wasRateLimited) {
    rateLimitedCount.add(1);
  }
}
