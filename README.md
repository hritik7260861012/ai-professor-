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
| 5+ | AI-generated study material | Not started |

### Test results

| Suite | Result |
| --- | --- |
| Backend (`./mvnw clean verify`) | ✅ **142/142** pass |
| Frontend (`npm test`) | ✅ **63/63** pass |
| Frontend (`npm run build`) | ✅ type-check + production bundle |

Backend tests run against H2 in PostgreSQL compatibility mode — no local
database required. **The migrations have not been run against a live
PostgreSQL instance**; do that before trusting them in production.

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

### First administrator

Public registration only creates `STUDENT` accounts. To bootstrap an admin,
export these before the first startup:

```bash
export APP_BOOTSTRAP_ADMIN_EMAIL="admin@college.edu"
export APP_BOOTSTRAP_ADMIN_PASSWORD="<strong-password>"
```

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

**Frontend**
- Landing, sign-in, sign-up, role dashboards
- Admin academic-structure workspace; teacher syllabus workspace; student syllabus view
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
- **No AI-generated notes, PPTs, assignments or MCQs yet** (Phase 5+).
- **Verified against H2 only**, not live PostgreSQL.

## Rules respected

- No secrets committed — all configuration via environment variables
- No fake production functionality
- Existing migrations are never edited; changes are new additive files
- Frontend and backend run independently during development