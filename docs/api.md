# API Reference — Phase 1

Base URL (local development): `http://localhost:8080/api/v1`

Interactive documentation: <http://localhost:8080/swagger-ui.html>
OpenAPI JSON: <http://localhost:8080/v3/api-docs>

All requests and responses use `application/json`. All failures use the
standard `ApiError` envelope (see
[architecture.md](architecture.md#error-model)).

---

## Health

### `GET /api/v1/health`

Liveness and database dependency check.

**Response `200 OK`**

```json
{
  "status": "UP",
  "version": "1.0.0",
  "database": "UP",
  "timestamp": "2026-01-01T12:00:00Z"
}
```

**Response `503 Service Unavailable`** — same shape, but
`"status": "DOWN"` and `"database": "DOWN"` when PostgreSQL is
unreachable.

The frontend landing page renders a live status pill from this endpoint.

---

## Authentication (Phase 2)

Both endpoints **validate input now** and always answer
`501 Not Implemented`. They are wired end-to-end so the frontend API
client, error handling, and OpenAPI contract are testable before
Phase 2 work begins.

### `POST /api/v1/auth/register`

```json
{
  "fullName": "Ada Lovelace",
  "email": "ada@example.com",
  "password": "super-secret",
  "role": "STUDENT"
}
```

Validation rules:

| Field | Rules |
| --- | --- |
| `fullName` | required, 2–100 characters |
| `email` | required, valid email address |
| `password` | required, 8–128 characters |
| `role` | required, one of `STUDENT`, `TEACHER`, `ADMIN` |

**Response `400 Bad Request`** (invalid input)

```json
{
  "timestamp": "2026-01-01T12:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/v1/auth/register",
  "details": {
    "fields": [
      { "field": "email", "message": "Email must be a valid address" }
    ]
  }
}
```

**Response `501 Not Implemented`** (valid input, Phase 1)

```json
{
  "timestamp": "2026-01-01T12:00:00Z",
  "status": 501,
  "error": "Not Implemented",
  "message": "Account registration goes live in Phase 2. Input validation is already enforced.",
  "path": "/api/v1/auth/register",
  "details": null
}
```

### `POST /api/v1/auth/login`

```json
{
  "email": "ada@example.com",
  "password": "super-secret"
}
```

Same validation and error behaviour as `register`. No session or token is
issued in Phase 1.

---

## Error status codes

| Status | When |
| --- | --- |
| 200 | Successful request |
| 400 | Validation failure or malformed JSON body |
| 404 | Unknown endpoint |
| 405 | HTTP method not allowed for the endpoint |
| 415 | Unsupported content type |
| 500 | Unexpected server error (logged with stack trace server-side) |
| 501 | Feature intentionally not built yet (Phase 1 auth stubs) |
| 503 | Health endpoint with the database unreachable |

---

## Conventions for future phases

- Every new feature gets its own controller under `/api/v1/<resource>`.
- DTOs are Java records with Bean Validation annotations; validation
  errors always surface as `400` with `details.fields`.
- Pagination, where needed, uses `?page=` / `?size=` with a
  `Page<T>`-shaped response.
- New schema changes go into `backend/src/main/resources/db/migration/`
  as `V<version>__<name>.sql` — never hand-edit the database.
