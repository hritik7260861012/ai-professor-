# API Reference

Base URL (local development): `http://localhost:8080/api/v1`

Interactive documentation: <http://localhost:8080/swagger-ui.html>
OpenAPI JSON: <http://localhost:8080/v3/api-docs>

All requests and responses use `application/json`, except the syllabus
upload (multipart) and download (binary) endpoints. All failures use the
standard `ApiError` envelope (see
[architecture.md](architecture.md#error-model)).

---

## Health

### `GET /api/v1/health`

Liveness and database dependency check. Returns `200 OK` with
`"status": "UP"` when both the API and PostgreSQL are reachable, or
`503 Service Unavailable` with `"database": "DOWN"` otherwise.

---

## Authentication

Session-based. `POST /api/v1/auth/login` returns a `UserDto` and sets an
**HttpOnly** `JSESSIONID` cookie. Nothing is stored in `localStorage`.

| Endpoint | Purpose |
| --- | --- |
| `POST /api/v1/auth/register` | Create a **STUDENT** account. Requesting `TEACHER`/`ADMIN` returns `403`. |
| `POST /api/v1/auth/login` | Sign in, establish the session cookie. |
| `POST /api/v1/auth/logout` | Invalidate the session (`204`). |
| `GET /api/v1/auth/me` | Current user, or `401`. |
| `GET /api/v1/auth/csrf` | CSRF token for this session. |

### CSRF

State-changing requests must carry the token from `/auth/csrf` in the
`X-CSRF-TOKEN` header. The token lives in the session and is never exposed
to JavaScript as a readable cookie.

```http
GET /api/v1/auth/csrf
→ 200 {"token":"b1f0…","headerName":"X-CSRF-TOKEN","parameterName":"_csrf"}

POST /api/v1/auth/login
X-CSRF-TOKEN: b1f0…
```

---

## Academic structure (Phase 3)

All readable by any authenticated account, except where noted.
List endpoints accept `?page=` / `?size=` and return a `PageResponse`:

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0,
  "first": true,
  "last": true
}
```

### Catalogue (any authenticated role)

| Endpoint | Returns |
| --- | --- |
| `GET /api/v1/departments` | Paged departments |
| `GET /api/v1/programs?departmentId=` | Paged programs |
| `GET /api/v1/academic-years` | Paged academic years |
| `GET /api/v1/semesters?academicYearId=` | Semesters (not paged; few per year) |

### Role-scoped reads

These are **filtered by role** — a teacher sees only what they are assigned
to teach, a student only what they are enrolled in. An inaccessible record
returns `404`, not `403`, so existence is never disclosed.

| Endpoint | ADMIN | TEACHER | STUDENT |
| --- | --- | --- | --- |
| `GET /api/v1/classes` | all classes | assigned classes | enrolled classes |
| `GET /api/v1/classes/{id}` | any | assigned | enrolled |
| `GET /api/v1/classes/{id}/subjects` | full syllabus | **only own subjects** | full syllabus |
| `GET /api/v1/classes/{id}/students` | any | assigned classes | — (`404`) |
| `GET /api/v1/classes/{id}/teachers` | any | assigned classes | — (`404`) |
| `GET /api/v1/subjects` | all subjects | assigned subjects | enrolled subjects |
| `GET /api/v1/subjects/{id}` | any | assigned | enrolled |
| `GET /api/v1/me/classes` | all classes | assigned | enrolled |
| `GET /api/v1/me/enrollments` | — | — | own enrollments |
| `GET /api/v1/me/assignments` | — | own assignments | — |

Note the deliberate asymmetry: a teacher's assignment for one subject never
exposes the rest of that class's syllabus.

### Administration (ADMIN only)

Under `/api/v1/admin/academic/**`, enforced by the security filter chain:

`departments`, `programs`, `academic-years`, `semesters`, `subjects`,
`classes` — each supporting `POST`, `PUT /{id}` and `DELETE /{id}`.

| Endpoint | Purpose |
| --- | --- |
| `POST /api/v1/admin/academic/classes/{classId}/subjects` | Offer a subject in a class |
| `DELETE /api/v1/admin/academic/classes/{classId}/subjects/{linkId}` | Remove it |
| `POST /api/v1/admin/academic/enrollments` | Enroll a student |
| `DELETE /api/v1/admin/academic/enrollments/{id}` | Unenroll |
| `POST /api/v1/admin/academic/teaching-assignments` | Assign a teacher to a subject in a class |
| `DELETE /api/v1/admin/academic/teaching-assignments/{id}` | Remove the assignment |

Deleting a parent that still has children returns `409` with an
explanation, rather than cascading silently.

---

## Syllabi (Phase 4)

### Lifecycle

```
UPLOADED ──► EXTRACTING ──► REVIEW_REQUIRED ──► APPROVED   ← students can read
               │                   │
               │                   └──► REJECTED
               ▼
        EXTRACTION_FAILED ──► (retry-extraction)
```

Re-uploading the same class + subject creates a **new version** and marks
the previous one no longer current. History and the original files are
never discarded.

### Supported formats

| Format | MIME type | Extension |
| --- | --- | --- |
| PDF | `application/pdf` | `.pdf` |
| DOCX | `application/vnd.openxmlformats-officedocument.wordprocessingml.document` | `.docx` |
| Text | `text/plain` | `.txt` |

Maximum **10 MB** (`app.syllabus.max-file-size`).

Validation is layered — extension, then declared MIME type, then the actual
**magic bytes**. A mismatch is rejected with a message naming both formats
(`"This file looks like a PDF but was uploaded as a DOCX."`).

Legacy binary `.doc`, scanned-image PDFs and password-protected PDFs are
rejected with an actionable message.

### Teacher endpoints

| Endpoint | Purpose |
| --- | --- |
| `POST /api/v1/teacher/syllabi` | Upload (multipart). Params: `classId`, `subjectId`, `file` |
| `GET /api/v1/teacher/syllabi` | Paged list of your uploads |
| `GET /api/v1/teacher/syllabi/{id}` | Metadata + structure + extracted text |
| `GET /api/v1/teacher/syllabi/{id}/file` | Authorized download of the original |
| `GET /api/v1/teacher/syllabi/{id}/extracted-content` | Extracted text and outline |
| `PUT /api/v1/teacher/syllabi/{id}/structure` | Replace the unit/topic tree |
| `POST /api/v1/teacher/syllabi/{id}/approve` | Approve → visible to students |
| `POST /api/v1/teacher/syllabi/{id}/reject` | Reject, optional `{ "reason": "…" }` |
| `POST /api/v1/teacher/syllabi/{id}/retry-extraction` | Re-run extraction on the original |
| `DELETE /api/v1/teacher/syllabi/{id}` | Delete the record and its file |

A teacher may only act on a syllabus they uploaded **and** for which they
still hold the teaching assignment. Revoking the assignment revokes access
immediately.

`PUT .../structure` replaces the whole tree; the request order defines
display order:

```json
{
  "units": [
    {
      "title": "Unit I: Foundations",
      "topics": [
        { "title": "1.1 History of computing", "subtopics": ["early machines"] }
      ]
    }
  ],
  "learningOutcomes": [{ "statement": "Explain binary representation", "unitIndex": null }]
}
```

Editing an `APPROVED` syllabus returns `409`; upload a new version instead.
Approving a syllabus with no units returns `409`.

### Student endpoints

| Endpoint | Returns |
| --- | --- |
| `GET /api/v1/student/syllabi` | Approved syllabi for your enrollments (newest version per placement) |
| `GET /api/v1/student/syllabi/{id}` | One approved syllabus |

Drafts, failed extractions and rejected syllabi return `404`, exactly like
records that do not exist. There are no approve, reject or edit routes on
the student API.

### Response shape

```json
{
  "syllabus": {
    "id": 1,
    "classCode": "SEC-A",
    "subjectCode": "CS101",
    "originalFilename": "syllabus.pdf",
    "status": "REVIEW_REQUIRED",
    "version": 1,
    "current": true,
    "extractionError": null
  },
  "units": [
    {
      "title": "Unit I: Foundations",
      "position": 0,
      "topics": [{ "title": "1.1 History", "position": 0, "subtopics": [] }]
    }
  ],
  "learningOutcomes": [{ "statement": "Explain binary", "position": 0, "unitId": null }],
  "extractedText": "Unit I: Foundations\n1.1 History",
  "warning": null
}
```

The internal storage key and any filesystem path are never serialised.

---

## Error status codes

| Status | When |
| --- | --- |
| 200 / 201 / 204 | Success / created / no content |
| 400 | Validation failure, unsupported upload, malformed body |
| 401 | No session, or bad credentials |
| 403 | Authenticated but not permitted (for example registering as `ADMIN`) |
| 404 | Unknown endpoint, or a record the caller may not see |
| 405 | Wrong HTTP method |
| 409 | Lifecycle or referential conflict |
| 413 | Body above the servlet multipart ceiling |
| 415 | Unsupported content type |
| 500 | Unexpected server error |

`404` is used deliberately in place of `403` for resources outside the
caller's scope, so an attacker cannot probe for their existence.

---

## Conventions

- Every feature gets its own controller under `/api/v1/<resource>`.
- DTOs are Java records with Bean Validation annotations; validation errors
  surface as `400` with `details.fields`.
- Pagination uses `?page=` / `?size=` returning `PageResponse<T>`.
- Schema changes go into `backend/src/main/resources/db/migration/` as
  `V<version>__<name>.sql` — never hand-edit an applied migration.