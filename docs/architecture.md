# Architecture - AI Professor Assistant

## Overview

AI Professor Assistant is a **web-first** college learning platform for
teachers, students, and administrators, built phase by phase.

| Phase | Delivered |
| --- | --- |
| 1 | Project scaffold, health endpoint, `ApiError` envelope, Flyway schema, OpenAPI |
| 2 | Session authentication (BCrypt), CSRF, ADMIN/TEACHER/STUDENT roles |
| 3 | Academic structure: departments, programs, years, semesters, subjects, classes, enrollments, teaching assignments |
| 4 | Syllabus upload, deterministic text extraction, teacher review, student read-only view |

The design keeps the Android client (later phases) possible without any
backend rework, because everything is a versioned JSON API under `/api/v1`.

```
┌──────────────────────┐        HTTPS/JSON        ┌──────────────────────┐
│  React + TypeScript  │ <──────────────────────> │  Spring Boot 3 (J17) │
│  Vite dev server     │                          │  REST API /api/v1     │
│  (built app: dist/)  │                          │                      │
└──────────────────────┘                          │  ┌────────────────┐  │
                                                  │  │ Flyway V1..V4  │  │
   OpenAPI spec                                   │  └───────┬────────┘  │
   /swagger-ui.html                               │          v           │
                                                  │  ┌────────────────┐  │
                                                  │  │ PostgreSQL 15+ │  │
                                                  │  │ private file   │  │
                                                  │  │ storage (V4)   │  │
                                                  │  └────────────────┘  │
                                                  └──────────────────────┘
```

## Key decisions

| Decision | Rationale |
| --- | --- |
| **REST under `/api/v1`** | Versioned prefix lets us evolve the API without breaking future clients. |
| **Single `ApiError` envelope** | Every failure returns `{ timestamp, status, error, message, path, details }` — web and mobile parse errors identically. |
| **Session cookies, not tokens** | The `JSESSIONID` is HttpOnly, so XSS cannot read it. Nothing is stored in `localStorage`. |
| **CSRF token in the session** | The token is fetched from `/auth/csrf` and echoed in `X-CSRF-TOKEN`. Never a JS-readable cookie. |
| **Authorization at the service layer** | Controllers do no access checks; `AcademicScopeService` and `SyllabusService` are the only choke points. |
| **404 rather than 403 for out-of-scope records** | An attacker must not learn that a record exists. |
| **Flyway for schema** | Version-controlled, runs on startup; the same SQL works locally or on Supabase. |
| **Never edit an applied migration** | `V1`–`V3` are frozen. Every change is a new additive `V<n>`. |
| **Storage abstraction** | `FileStorageService` keeps the local provider swappable for private cloud object storage later. |
| **Deterministic extraction** | No AI, no API key. Same input always yields the same outline; low confidence returns text, not invention. |
| **Config via env vars** | No secrets in code or git. |

## Backend layout

```
backend/src/main/java/com/professor/assistant/
  config/       CorsConfig, SecurityConfig, SyllabusProperties, OpenApiConfig
  controller/   Health, Auth, Admin*, Academic, TeacherSyllabus, StudentSyllabus
  domain/       User, Role, AcademicClass, Subject, Syllabus + children
  dto/          Records with Bean Validation; PageResponse
  exception/    ApiError + GlobalExceptionHandler
  repository/   Spring Data JPA
  security/     AppUserDetails (principal carrying the user id)
  service/      AuthService, Academic{Scope,Query,Structure}Service, SyllabusService
  storage/      FileStorageService + LocalPrivateFileStorageService
  syllabus/     ExtractionService, TextExtractors, StructureParser, Validator
  web/          Principal helper, REST entry point / denied handler, error controllers
```

### Data model

| Phase | Tables |
| --- | --- |
| 1 | `app_user` |
| 2 | `app_user.status` (column added by `V2`) |
| 3 | `department`, `academic_program`, `academic_year`, `semester`, `subject`, `academic_class`, `class_subject`, `teaching_assignment`, `enrollment` |
| 4 | `syllabus`, `syllabus_unit`, `syllabus_topic`, `syllabus_subtopic`, `syllabus_learning_outcome` |

`academic_class` is the table name because `CLASS` is a reserved SQL word.

Phase 4 links to the Phase 3 entities (`academic_class`, `subject`,
`semester`) rather than duplicating them.

## Frontend layout

```
frontend/src/
  api/client.ts       Typed fetch wrapper, ApiError, CSRF + credential handling
  auth/               AuthContext (session), ProtectedRoute (role guard)
  components/
    academic/         ClassBrowser
    layout/           Navbar, Footer, Layout
    ui/               Button, Input, Select, Textarea, FileInput, Card, Badge, Alert
  pages/              Landing, Login, Register, Dashboards, AdminAcademic,
                      TeacherSyllabi, StudentSyllabi
```

## Error model

All endpoints return the same envelope on failure:

```json
{
  "timestamp": "2026-01-01T12:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/v1/auth/register",
  "details": { "fields": [{ "field": "email", "message": "Email must be a valid address" }] }
}
```

## Syllabus pipeline (Phase 4)

```
multipart upload
   → validator   extension → MIME type → magic bytes → size
   → storage     random key, private directory
   → extractor   PDFBox / POI / UTF-8, page- and character-capped
   → parser      deterministic Unit / Topic / Subtopic detection
   → syllabus    EXTRACTING → REVIEW_REQUIRED  (or EXTRACTION_FAILED)
   → teacher     edits the tree, then APPROVED or REJECTED
   → student     reads APPROVED rows for enrolled classes only
```

Extraction runs synchronously in this phase, bounded by the configured
limits. A background worker can later be introduced behind the same
service interface without changing controllers.

### File handling safety

- Filenames are sanitized for display only; a random 32-hex key is the sole
  filesystem identifier.
- Storage keys are validated against a strict pattern and re-checked to
  resolve inside the storage root, so traversal keys cannot escape.
- Uploads are validated on extension, MIME type **and** magic bytes; a
  mismatch is rejected rather than silently re-typed.
- Extracted document text is never logged.

## What is deferred

- OCR for scanned documents
- AI-generated notes, PPTs, assignments, MCQs (later phases)
- Private cloud object storage (the interface is ready; only a local
  provider is configured)
- The Android application (will consume the same `/api/v1` contract)

See [docs/api.md](api.md) for the API surface and [docs/setup.md](setup.md)
for local setup.