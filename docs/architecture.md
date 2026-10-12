# Architecture — AI Professor Assistant (Phase 1)

## Overview

AI Professor Assistant is a **web-first** college learning platform for
teachers, students, and administrators. Phase 1 establishes the project
foundation; the design intentionally keeps the Android client (Phase 3+)
possible without any backend rework.

```
┌────────────────────────┐        HTTPS/JSON         ┌──────────────────────────┐
│  React + TypeScript    │  ───────────────────────▶ │  Spring Boot 3 (Java 17) │
│  Vite dev server :5173 │◀───────────────────────   │  REST API /api/v1  :8080 │
│  (built app: dist/)    │                            │                          │
└────────────────────────┘                            │   ┌──────────────────┐  │
        │                                             │   │ Flyway migrations │  │
        │                                             │   └────────┬─────────┘  │
        ▼                                             │            ▼            │
   OpenAPI spec                                        │  ┌──────────────────┐   │
   /swagger-ui.html                                    │  │ PostgreSQL 15+   │   │
                                                     │  │ (Supabase-ready) │   │
                                                     │  └──────────────────┘   │
                                                     └──────────────────────────┘
```

## Key decisions

| Decision | Rationale |
| --- | --- |
| **REST under `/api/v1`** | Versioned prefix lets us evolve the API without breaking the future Android client. |
| **Single `ApiError` envelope** | Every failure returns `{ timestamp, status, error, message, path, details }` — web and mobile clients parse errors identically. |
| **Stateless JWT-ready design** | Auth endpoints exist as validation-only stubs (501). Real registration/sign-in arrives in Phase 2 with the same URL contract. |
| **Flyway for schema** | Migrations are version-controlled and run automatically at startup; the same SQL works on a local PostgreSQL or a Supabase project. |
| **Configuration via env vars** | No secrets in code or git. Spring reads `SPRING_DATASOURCE_*`, Vite reads `VITE_*`. |
| **Independent dev servers** | Frontend (`vite`) and backend (`mvn spring-boot:run`) run separately; CORS and the Vite proxy are configured for local development. |
| **No local/embedded AI** | Per project rules, AI features are deferred to later phases and will be served by remote AI services, never bundled locally. |

## Backend layout

```
backend/
├── pom.xml
└── src/main/java/com/professor/assistant/
    ├── AssistantApplication.java        # Spring Boot entry point
    ├── config/CorsConfig.java           # Origins from app.cors.allowed-origins
    ├── controller/
    │   ├── HealthController.java        # GET /api/v1/health (incl. DB check)
    │   └── AuthController.java          # POST /api/v1/auth/{register,login} (501 stubs)
    ├── domain/                          # User entity, Role enum
    ├── dto/                             # RegisterRequest, LoginRequest (validated records)
    ├── exception/                       # ApiError envelope + GlobalExceptionHandler
    ├── repository/                      # Spring Data JPA repositories
    └── web/                             # AccessLogFilter, container ErrorController
```

## Frontend layout

```
frontend/
├── src/
│   ├── api/client.ts                    # Typed fetch wrapper → ApiError
│   ├── components/
│   │   ├── layout/                      # Navbar, Footer, Layout (Outlet shell)
│   │   └── ui/                          # Button, Input, Select, Textarea, Card, Badge, Alert
│   ├── pages/                           # Landing, Login, Register, role dashboards, 404
│   └── lib/utils.ts                     # cx() classname helper
```

## Data model (Phase 1)

`app_user` — one row per platform user:

| Column | Type | Notes |
| --- | --- | --- |
| `id` | BIGINT | Auto-generated identity |
| `email` | VARCHAR(255) | Unique |
| `password_hash` | VARCHAR(255) | Not populated until Phase 2 |
| `full_name` | VARCHAR(100) | |
| `role` | VARCHAR(20) | `STUDENT`, `TEACHER`, or `ADMIN` (check constraint) |
| `created_at` / `updated_at` | TIMESTAMP | Maintained by the entity |

## Error model

All endpoints return the same envelope on failure:

```json
{
  "timestamp": "2026-01-01T12:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/v1/auth/register",
  "details": {
    "fields": [{ "field": "email", "message": "Email must be a valid address" }]
  }
}
```

Status codes used in Phase 1: `200` OK, `400` validation/body errors,
`404` unknown endpoint, `405` wrong HTTP method, `501` feature not
implemented yet, `503` health check with DB down, `500` unexpected.

## What is explicitly deferred

- Authentication (registration, sign-in, password hashing, sessions/JWT)
- Courses, assignments, grading, attendance, messaging
- AI features (lesson prep, tutoring, analytics)
- The Android application (will consume the same `/api/v1` contract)

See [docs/api.md](api.md) for the API surface and [docs/setup.md](setup.md)
for local setup.
