# ShortLink

ShortLink is a URL shortener backend built to demonstrate production-minded backend engineering with Spring Boot.

Current status: `Phase 1 completed`

## What Phase 1 Delivers

- Create short URLs
- Optional custom alias
- Optional expiration time
- Redirect short URLs with `302 Found`
- Unified JSON error handling
- Flyway-managed PostgreSQL schema

## Tech Stack

- Java 21
- Spring Boot 4
- Spring Web MVC
- Spring Data JPA
- PostgreSQL
- Flyway
- Maven
- Testcontainers

## Core API

### Create short URL

```bash
curl -i -X POST http://localhost:8080/api/v1/urls \
  -H "Content-Type: application/json" \
  -d '{
    "originalUrl": "https://example.com/landing-page"
  }'
```

### Redirect

```bash
curl -i http://localhost:8080/aB3xK7c
```

Expected:

```http
HTTP/1.1 302 Found
Location: https://example.com/landing-page
```

## Example Response

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "shortCode": "aB3xK7c",
  "shortUrl": "http://localhost:8080/aB3xK7c",
  "originalUrl": "https://example.com/landing-page",
  "totalClicks": 0,
  "expiresAt": null,
  "createdAt": "2026-03-27T08:00:00Z",
  "updatedAt": "2026-03-27T08:00:00Z"
}
```

## Error Format

```json
{
  "error": "INVALID_URL",
  "message": "Only http and https protocols are allowed",
  "status": 400,
  "timestamp": "2026-03-27T10:30:00Z",
  "path": "/api/v1/urls"
}
```

## Design Notes

- Random 7-character Base62 short codes
- `302` is used instead of `301` so every click still reaches the service
- URL validation blocks invalid schemes and private or local addresses
- A seeded user is used temporarily until authentication is added in Phase 3
- `open-in-view: false` keeps persistence logic inside service boundaries
- Structured JSON logging is configured with Logback

## Configuration

Common application settings live in `src/main/resources/application.yaml`.

Development datasource settings live in `src/main/resources/application-dev.yaml` and use environment-variable-first configuration:

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`

If these variables are not provided, local development defaults are used.

## Testing

Phase 1 test coverage includes:

- `Base62Encoder`
- `UrlValidator`
- `UrlShorteningService`
- `RedirectService`
- `UrlController`
- `RedirectController`
- `UrlCrudIntegrationTest`
- `RedirectFlowIntegrationTest`

Result:

```text
Tests run: 25, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Run Locally

Make sure PostgreSQL is running locally, then start the app:

```bash
./mvnw spring-boot:run
```

Run tests with:

```bash
./mvnw -U test
```

Integration tests use Testcontainers with PostgreSQL, so Docker must be running before executing the full test suite.

## Next

Phase 2 will add:

- Redis caching
- Asynchronous click tracking
- Event persistence and aggregation
