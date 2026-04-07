import http from 'k6/http';
import { check, fail } from 'k6';

const jsonHeaders = {
  'Content-Type': 'application/json',
  Accept: 'application/json',
};

export function resolveBaseUrl() {
  return (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/+$/, '');
}

export function resolvePassword() {
  return __ENV.K6_PASSWORD || 'ShortLink9';
}

export function createUserCredentials(prefix) {
  const nonce = Date.now();
  return {
    email: `${prefix}-${nonce}@example.com`,
    password: resolvePassword(),
    name: `${prefix} user`,
  };
}

export function registerAndLogin(baseUrl, credentials) {
  const registerResponse = http.post(
    `${baseUrl}/api/v1/auth/register`,
    JSON.stringify(credentials),
    { headers: jsonHeaders }
  );

  check(registerResponse, {
    'register returns 201': (response) => response.status === 201,
  }) || fail(`Register failed: ${registerResponse.status} ${registerResponse.body}`);

  const loginResponse = http.post(
    `${baseUrl}/api/v1/auth/login`,
    JSON.stringify({
      email: credentials.email,
      password: credentials.password,
    }),
    { headers: jsonHeaders }
  );

  check(loginResponse, {
    'login returns 200': (response) => response.status === 200,
    'login returns an access token': (response) => Boolean(response.json('accessToken')),
  }) || fail(`Login failed: ${loginResponse.status} ${loginResponse.body}`);

  return {
    accessToken: loginResponse.json('accessToken'),
    refreshToken: loginResponse.json('refreshToken'),
  };
}

export function authHeaders(accessToken) {
  return {
    ...jsonHeaders,
    Authorization: `Bearer ${accessToken}`,
  };
}

export function createShortUrl(baseUrl, accessToken, payload) {
  const response = http.post(
    `${baseUrl}/api/v1/urls`,
    JSON.stringify(payload),
    {
      headers: authHeaders(accessToken),
    }
  );

  check(response, {
    'create short url returns 201': (res) => res.status === 201,
    'create short url returns short code': (res) => Boolean(res.json('shortCode')),
  }) || fail(`Create URL failed: ${response.status} ${response.body}`);

  return response.json();
}

export function listUrls(baseUrl, accessToken, extraParams = {}) {
  return http.get(`${baseUrl}/api/v1/urls`, {
    headers: authHeaders(accessToken),
    ...extraParams,
  });
}

export function deleteUrl(baseUrl, accessToken, shortCode, extraParams = {}) {
  return http.del(`${baseUrl}/api/v1/urls/${shortCode}`, null, {
    headers: authHeaders(accessToken),
    ...extraParams,
  });
}
