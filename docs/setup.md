# Setup Guide

## Prerequisites

| Tool | Version | Check |
| --- | --- | --- |
| Java (JDK) | 17+ | `java -version` |
| Maven | 3.9+ | `mvn -version` (or use `./mvnw` in `backend/`) |
| Node.js | 20+ | `node --version` |
| npm | 10+ | `npm --version` |
| PostgreSQL | 15+ (or a Supabase project) | `psql --version` |

## 1. Clone and configure

```bash
git clone <repo-url>
cd "profeser assistence"

# Copy and edit — .env is gitignored.
cp .env.example .env
```

Spring Boot does **not** read `.env` files automatically. Export the
variables in your shell, or set them in your IDE run configuration. Vite
does read `VITE_*` from `frontend/.env` automatically.

```bash
export SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/professor_assistant"
export SPRING_DATASOURCE_USERNAME="professor_assistant"
export SPRING_DATASOURCE_PASSWORD="<your-password>"
```

### Using Supabase instead of a local database

1. Create a Supabase project and copy the connection string.
2. Export it as `SPRING_DATASOURCE_URL` (prepend `jdbc:`).
3. Flyway creates the schema automatically on startup.

## 2. Run the backend

```bash
cd backend
mvn spring-boot:run        # or: ./mvnw spring-boot:run
```

- API: <http://localhost:8080/api/v1/health>
- OpenAPI UI: <http://localhost:8080/swagger-ui.html>

First startup runs all Flyway migrations (`V1`–`V4`).

### Creating the first administrator

Public registration only ever creates a `STUDENT`. To bootstrap an admin,
export these **before** first startup:

```bash
export APP_BOOTSTRAP_ADMIN_EMAIL="admin@college.edu"
export APP_BOOTSTRAP_ADMIN_PASSWORD="<strong-password>"
```

The runner is idempotent: it will not recreate the account on restart.
Change the password after the first sign-in.

## 3. Run the frontend

```bash
cd frontend
npm install
npm run dev                # http://localhost:5173
```

The landing page health pill degrades gracefully to "Backend offline".

## 4. Run tests

**Backend** (H2 in PostgreSQL compatibility mode — no PostgreSQL needed):

```bash
cd backend
mvn clean verify           # or: ./mvnw clean verify
```

**Frontend:**

```bash
cd frontend
npm test
npm run build              # type-checks (tsc) then bundles into dist/
```

---

## Phase 4: syllabus uploads

### Supported formats and limits

| Setting | Default | Env var |
| --- | --- | --- |
| Max upload | 10 MB | `APP_SYLLABUS_MAX_FILE_SIZE` |
| Servlet ceiling | 11 MB | `APP_SYLLABUS_MAX_UPLOAD_SIZE` |
| Max extracted characters | 500,000 | `APP_SYLLABUS_MAX_CHARACTERS` |
| Max PDF pages read | 300 | `APP_SYLLABUS_MAX_PDF_PAGES` |

Keep `APP_SYLLABUS_MAX_UPLOAD_SIZE` slightly **above**
`APP_SYLLABUS_MAX_FILE_SIZE`. The application limit produces a friendly
`400`; the servlet ceiling is a last-resort `413`.

### Private storage

Uploaded files are written to `APP_SYLLABUS_STORAGE_DIR`
(default `./private-storage/syllabi`). This directory:

- is **outside** `frontend/public`, so nothing is served as a static asset;
- is never exposed over HTTP — downloads go through
  `GET /api/v1/teacher/syllabi/{id}/file`, which is authenticated and
  authorized;
- uses a random 32-hex-character key per file. The uploaded filename is
  display metadata only and never becomes a path.

To point at storage outside the project directory, for example a mounted
volume:

```bash
# Linux/macOS
export APP_SYLLABUS_STORAGE_DIR=/var/lib/professor-assistant/syllabi

# Windows PowerShell
$env:APP_SYLLABUS_STORAGE_DIR = "D:\professor-data\syllabi"
```

Make sure the backend process can write to it.

### Cleaning up stored files safely

Stored files are removed automatically when a syllabus is deleted via
`DELETE /api/v1/teacher/syllabi/{id}`. Files orphaned by a crash are not
swept automatically.

```bash
# 1. Stop the backend first — otherwise a running upload is lost.
# 2. Then remove the directory contents.
rm -rf ./private-storage/syllabi/*
```

The directory is recreated on next start. `private-storage/` is
gitignored.

### Trying it out

1. Sign in as (or bootstrap) an `ADMIN`.
2. Create a department → program → academic year → semester → subject →
   class, and offer the subject in the class.
3. Create a `TEACHER` account, enroll a `STUDENT`, and assign the teacher.
4. Sign in as the teacher, open **Dashboard → Manage syllabi**, upload a
   PDF/DOCX/TXT file, review the extracted units, then approve.
5. Sign in as the student and open **Dashboard → My syllabi**.

---

## Known limitations

- **No OCR.** A scanned-image PDF yields no text. The app says so
  explicitly and asks for a text-based file or manual entry. It does not
  pretend extraction succeeded.
- **Legacy `.doc` is not supported** (binary Word format). Convert to
  `.docx`.
- **No AI-generated study material yet** — no notes, PPTs, assignments or
  MCQs. Those are later phases.
- **Extraction is synchronous.** A large upload holds its HTTP request for
  the duration of the parse. Bounded by the size, page and character caps.
  A background worker can be introduced later behind the same service
  interface.
- **Parsing is best-effort and deterministic.** When no unit headings are
  recognised, nothing is invented: the raw text is kept and the teacher
  organises it by hand.
- **Phase 1–4 are verified against H2**, not a live PostgreSQL instance.
  Run the migrations against PostgreSQL before trusting them in production.

---

## Troubleshooting

| Symptom | Fix |
| --- | --- |
| Backend exits with a Flyway/connection error | Start PostgreSQL, or correct `SPRING_DATASOURCE_URL` |
| `npm install` fails on network | Check your registry/proxy; retry with `--verbose` |
| Landing page says "Backend offline" | Expected — start the backend |
| Port 5173 or 8080 in use | `SERVER_PORT=<port>`; `npm run dev -- --port <port>` |
| Syllabus upload returns 413 | Raise `APP_SYLLABUS_MAX_UPLOAD_SIZE` **and** `APP_SYLLABUS_MAX_FILE_SIZE` |
| Syllabus upload returns "could not be read" | The file is corrupt, or an unsupported legacy format |
| Extraction says OCR is unsupported | It is a scanned image — upload a text-based file |
| Storage errors on startup | Ensure `APP_SYLLABUS_STORAGE_DIR` exists and is writable |