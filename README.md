# AI Professor Assistant

A **web-first** AI learning platform for college teachers, students, and
administrators. The architecture lets a future Android application reuse
the same REST APIs, authentication and database.

## Status

| Phase | Scope | Result |
| --- | --- | --- |
| 1 | Scaffold, health, `ApiError`, migrations, OpenAPI | ✅ |
| 2 | Session auth, BCrypt, CSRF, ADMIN/TEACHER/STUDENT | ✅ |
| 3 | Academic structure, enrollment, teaching assignments | ✅ |
| 4 | Syllabus upload, extraction, teacher review | ✅ |
| 5 | AI content generation, teacher review, student release | ✅ (uncommitted) |

### Test results

| Suite | Result |
| --- | --- |
| Backend (`./mvnw clean verify`) | ✅ **219/219** pass |
| Frontend (`npm test`) | ✅ **91/91** pass |
| Frontend (`npm run build`) | ✅ type-check + production bundle |

Backend tests run against H2 in PostgreSQL compatibility mode — no local
database required. **The migrations have not been run against a live
PostgreSQL instance**; do that before trusting them in production.

**No real Gemini request has been made.** Every automated test runs against the
deterministic `MOCK` provider or a stubbed provider, with no API key and no
network call. A live Google Gemini call and a live PostgreSQL run both require
separate manual verification.

## Quick start

```bash
# 1. Configure
cp .env.example .env                  # then edit; .env is gitignored
# Spring Boot does not read .env automatically — export the variables,
# or set them in your IDE run configuration.

# 2. Backend (Java 17, Maven 3.9+, PostgreSQL 15+)
cd backend
./mvnw spring-boot:run                # Windows: mvnw.cmd spring-boot:run

# 3. Frontend (Node 20+)
cd frontend
npm install
npm run dev                           # http://localhost:5173
```

- API base URL: `http://localhost:8080/api/v1`
- Health: `http://localhost:8080/api/v1/health`
- OpenAPI UI: `http://localhost:8080/swagger-ui.html`

### Troubleshooting startup

**`Connection to localhost:5432 refused`** — PostgreSQL is not running. Point
`SPRING_DATASOURCE_URL` / `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD`
at a real server. A throwaway one for local work:

```bash
docker run -d --name professor-assistant-db \
  -e POSTGRES_USER=professor_assistant \
  -e POSTGRES_PASSWORD=change-me \
  -e POSTGRES_DB=professor_assistant \
  -p 5432:5432 postgres:16
```

**`FATAL: invalid value for parameter "TimeZone": "Asia/Calcutta"`** — some JVMs
(commonly Windows) report the legacy `Asia/Calcutta` alias, which PostgreSQL
rejects. Force UTC:

```bash
# bash / macOS / Git Bash
export JAVA_TOOL_OPTIONS="-Duser.timezone=UTC"

# Windows PowerShell
$env:JAVA_TOOL_OPTIONS = "-Duser.timezone=UTC"
```

The app stores and compares timestamps in UTC, so this changes no behaviour.

**`Unsupported Database: PostgreSQL x.y`** — `flyway-database-postgresql` is
missing from the classpath. Flyway 10 moved per-database support out of
`flyway-core`, so H2 works in tests while PostgreSQL cannot start. The
dependency is declared in `backend/pom.xml`.

### AI generation (Phase 5)

Generation needs a provider credential. The app **starts normally without
one** — only generation requests fail, with a clear configuration message.

```bash
# Real provider (Google Gemini free tier). Create a key at
# https://aistudio.google.com/apikey and export it in the BACKEND shell.
export GEMINI_API_KEY="<your-key>"
export APP_AI_PROVIDER=GEMINI

# OR deterministic, offline, no key — useful for demos and local work.
export APP_AI_PROVIDER=MOCK
```

`MOCK` is **never selected automatically**. A production server with a missing
key returns `503` rather than quietly serving fabricated study material. Never
put the key in frontend code, a `VITE_*` variable, a commit or a log — it is
read only from the backend environment. All `APP_AI_*` variables and their
defaults are listed in [.env.example](.env.example).

Provider pricing, free-tier availability and rate limits are set by Google and
can change at any time. The daily quota is counted from the database and is
**advisory, not billing-grade**.

### First administrator (local development)

There is **no public admin-registration endpoint** — public sign-up always
creates a `STUDENT`, and requesting any other role is rejected with `403`. To
get the first `ADMIN` on a fresh local database, set the master switch **and**
the credentials before starting the backend:

```powershell
$env:APP_BOOTSTRAP_ADMIN_ENABLED  = "true"
$env:APP_BOOTSTRAP_ADMIN_EMAIL    = "<your-admin-email>"
$env:APP_BOOTSTRAP_ADMIN_PASSWORD = "<strong-password>"
```

`APP_BOOTSTRAP_ADMIN_ENABLED` defaults to **`false`**, so the credentials on
their own do nothing and leaving them set cannot silently create accounts on a
later start.

Properties of the bootstrap:

- **BCrypt**, using the same encoder as every other password
- **Idempotent** — if an administrator already exists, or an account already
  uses that email, nothing is created and **no password is overwritten**
- **Never logs the password**; only the email address is written
- Credentials come from the environment only and are never committed

Then sign in at `/login` with that email and password. On a real deployment,
leave the switch off and promote accounts through the admin UI instead.

#### Forgot the bootstrap password?

`APP_BOOTSTRAP_ADMIN_ENABLED` intentionally never overwrites an existing
administrator, so if you lose the original password you cannot sign in with
it again. For local development there is a separate, explicit switch:

```powershell
$env:APP_BOOTSTRAP_ADMIN_EMAIL          = "<your-admin-email>"
$env:APP_BOOTSTRAP_ADMIN_PASSWORD       = "<new-password>"
$env:APP_BOOTSTRAP_ADMIN_RESET_PASSWORD = "true"
```

This resets the password of an account that **already holds ADMIN**. It
defaults to `false`, the component does not exist unless it is explicitly
`true`, it never creates an account, it refuses to touch a non-ADMIN account,
and there is **no HTTP endpoint** for it. Set the switch back to `false` once
you have signed in.

Full instructions: **[docs/setup.md](docs/setup.md)** ·
API reference: **[docs/api.md](docs/api.md)** ·
Architecture: **[docs/architecture.md](docs/architecture.md)**

## What's included

**Authentication & access**
- Session cookies (HttpOnly `JSESSIONID`); nothing in `localStorage`
- CSRF token held in the session, echoed in the `X-CSRF-TOKEN` header
- ADMIN / TEACHER / STUDENT roles, enforced server-side
- Records outside the caller's scope return `404`, not `403`, so their
  existence is never disclosed

**Academic structure**
- Departments, programs, academic years, semesters, subjects, classes
- Teacher assignments (many-to-many), student enrollments
- Teachers see only assigned classes and subjects; students only enrolled ones

**Syllabi**
- PDF / DOCX / TXT upload, 10 MB limit, private storage
- Validation on extension, MIME type **and** magic bytes
- Deterministic unit/topic/subtopic extraction (no AI, no API key)
- Teacher review screen: edit, reorder, approve, reject, retry
- Students read approved syllabi for their enrollments only
- Versioned: re-uploading creates a new version, keeping history

**AI-generated study material (Phase 5)**
- Google Gemini behind a `ContentGenerationProvider` interface; model and
  endpoint configurable; API key read only from the backend environment
- Deterministic `MOCK` provider for tests and demos — no key, no network
- Six content types: unit-wise notes, teacher study material, PPT slide
  content, assignments & questions, MCQs, quick revision
- English / Hindi / Hinglish; three detail levels; three difficulty levels
- Grounded in an **approved syllabus only**; unit/topic scope passed to the model
- Asynchronous jobs (`QUEUED → RUNNING → COMPLETED | FAILED | CANCELLED`) on a
  bounded executor; the teacher polls and the page stops polling on unmount
- Atomic job claiming, bounded retries for retryable failures only, and
  startup reconciliation of jobs left behind by a crash
- Structured output (MCQs/slides/questions) validated before storage —
  malformed content fails the job instead of reaching a student
- Every option re-validated on the backend; frontend checks are never the authority
- Cost guards: per-user concurrency, daily quota, duplicate suppression,
  item/size caps, provider timeouts
- Teacher review: edit draft, approve, approve-and-release, reject with a reason
- **Teacher study material can never be released to students**, even if requested
- Content tied to a replaced syllabus version is marked `OUTDATED` and locked
- Students see only approved, released, non-outdated, student-audience content
  for classes they are actively enrolled in

**Frontend**
- Landing, sign-in, sign-up, role dashboards
- Admin academic-structure workspace; teacher syllabus workspace; student syllabus view
- Teacher AI generation workspace (`/teacher/content`): request form, job
  status, polling with cleanup, review and release workflow
- Student released-material list (`/student/content`)
- Typed API client, shared design system, role-aware navigation

## Repository layout

```
.
├── docs/                  # architecture.md, api.md, setup.md
├── frontend/              # React + TypeScript + Vite + Tailwind
├── backend/               # Spring Boot 3 + Java 17 + Maven (./mvnw wrapper)
├── private-storage/       # uploaded syllabus files (gitignored)
├── .env.example           # every environment variable, documented
└── README.md
```

## Known limitations

- **No OCR.** Scanned-image PDFs yield no text; the app says so explicitly
  and asks for a text-based file instead of pretending to succeed.
- **Legacy `.doc` is unsupported** — convert to `.docx`.
- **Extraction is synchronous**, bounded by configurable size/page/character
  caps. A background worker can replace it behind the same interface.
- **Parsing is best-effort.** With no recognisable unit headings, nothing is
  invented: the raw text is kept and the teacher organises it manually.
- **No real AI request has been verified.** All tests use the `MOCK` provider
  or a stub. A live Gemini call — including its cost, latency, rate limits and
  actual output quality — is unverified and must be checked separately.
- **No PPTX export.** Phase 5 generates *slide content* (titles, bullets,
  speaker notes, suggested diagrams). A downloadable `.pptx` is Phase 6 work.
- **The daily quota is advisory.** It is counted from the database, not from
  provider billing, so it is not a hard spend guarantee.
- **Jobs are never auto-retried after a restart.** Stale jobs are cancelled so
  a teacher can retry deliberately; nothing re-spends provider quota silently.
- **Verified against H2 only**, not live PostgreSQL.
- **Phase 5 is not committed yet.** Everything above for Phase 5 is currently
  uncommitted working-tree state.

## Rules respected

- No secrets committed — all configuration via environment variables
- No fake production functionality
- Existing migrations are never edited; changes are new additive files
- Frontend and backend run independently during development