# AI Professor Assistant

A **web-first** AI learning platform for college teachers, students, and
administrators. The architecture lets a future Android application reuse
the same REST APIs, authentication, database, and AI services — the
Android app itself is **not** part of this phase.

> **Phase 1** — project foundation: monorepo scaffold, design system,
> versioned REST API, health check, structured errors, database
> migrations, OpenAPI docs, and tests. Authentication, courses,
> assignments, and AI features ship in later phases.

## Status of Phase 1

| Area | Result |
| --- | --- |
| Backend (`mvn test`) | ✅ 9/9 tests pass — health, validation, error model, JPA + Flyway |
| Frontend (`npm test`) | ✅ 10/10 tests pass — API client, design system, landing page |
| Frontend (`npm run build`) | ✅ TypeScript type-check + production bundle |
| Live smoke test | ✅ `GET /api/v1/health` → `UP`; 400/404/405/501 all return the structured `ApiError` envelope |
| OpenAPI | ✅ `/v3/api-docs` + Swagger UI at `/swagger-ui.html` |

## Quick start

```bash
# 1. Configure (copy .env.example → .env and edit)
#    Backend: SPRING_DATASOURCE_URL / SPRING_DATASOURCE_USERNAME / SPRING_DATASOURCE_PASSWORD
#    Frontend: VITE_API_BASE_URL

# 2. Start the backend (Java 17, Maven 3.9+, PostgreSQL 15+)
cd backend
./mvnw spring-boot:run            # Windows: mvnw.cmd spring-boot:run

# 3. Start the frontend (Node 20+)
cd frontend
npm install
npm run dev                        # http://localhost:5173
```

Full instructions: **[docs/setup.md](docs/setup.md)**

- API reference: **[docs/api.md](docs/api.md)** · Architecture: **[docs/architecture.md](docs/architecture.md)**
- API base URL: `http://localhost:8080/api/v1`
- Health check: `http://localhost:8080/api/v1/health`

## What's included in Phase 1

**Backend (Spring Boot 3, Java 17)**
- `GET /api/v1/health` — service + database liveness (503 when the DB is down)
- `POST /api/v1/auth/register`, `POST /api/v1/auth/login` — fully validated,
  intentionally return `501 Not Implemented` until Phase 2 (no fake auth)
- Consistent `ApiError` envelope for every failure (400/404/405/415/500/501)
- Bean Validation, CORS via env vars, structured access logging
- Flyway migration `V1__init.sql` creating `app_user` (works on local
  PostgreSQL and Supabase)
- Tests run against H2 in PostgreSQL compatibility mode — no local DB needed

**Frontend (React 18, TypeScript, Vite, Tailwind)**
- Landing page with a live backend status pill
- Login and Register pages with client + server round-trip validation
- Role dashboard placeholders for Student / Teacher / Admin
- Reusable design system: `Button`, `Input`, `Select`, `Textarea`, `Card`,
  `Badge`, `Alert`, responsive `Navbar` with mobile menu, `Footer`
- Typed API client that parses the backend `ApiError` envelope
- Independent dev server with optional `/api` proxy to the backend

## Repository layout

```
.
├── docs/                  # architecture.md, api.md, setup.md
├── frontend/              # React + TypeScript + Vite + Tailwind
├── backend/               # Spring Boot 3 + Java 17 + Maven (with ./mvnw wrapper)
├── .env.example           # every environment variable, documented
├── .gitignore
└── README.md
```

## Rules respected in this phase

- No secrets committed — all configuration via environment variables
- No local AI / Ollama — AI features are deferred to later phases
- No fake production functionality — auth stubs are explicit and honest
- Frontend and backend run independently during development

## What's next (not started)

- Phase 2: real authentication (registration, password hashing, sessions/JWT)
- Phase 3: courses, assignments, grading assistance
- Phase 4: AI service integration (remote provider)
- Phase 5: Android app on top of the same `/api/v1` contract

**Phase 1 is complete. Stop here — do not start Phase 2 automatically.**
