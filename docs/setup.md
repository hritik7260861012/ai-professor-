# Setup Guide — Phase 1

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

# Backend: point Spring Boot at your database
cp .env.example .env          # then edit — .env is gitignored

# On Linux/macOS, export the variables (PowerShell/Windows users:
# set them in the terminal or IDE run configuration instead)
export SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/professor_assistant"
export SPRING_DATASOURCE_USERNAME="professor_assistant"
export SPRING_DATASOURCE_PASSWORD="<your-password>"

# Frontend: tell Vite where the API lives
cp frontend/.env.example frontend/.env
```

### Using Supabase instead of a local database

1. Create a project in Supabase.
2. In the database settings, find the connection string
   (`postgresql://<user>:<password>@db.<project-ref>.supabase.co:5432/postgres`).
3. Export it as `SPRING_DATASOURCE_URL` (prepend `jdbc:`):
   `jdbc:postgresql://db.<project-ref>.supabase.co:5432/postgres`.
4. Flyway creates the schema automatically on startup.

## 2. Run the backend

```bash
cd backend
mvn spring-boot:run        # or: ./mvnw spring-boot:run
```

- API: <http://localhost:8080/api/v1/health>
- OpenAPI UI: <http://localhost:8080/swagger-ui.html>

First startup runs the Flyway migration that creates `app_user`.

## 3. Run the frontend

```bash
cd frontend
npm install
npm run dev                # http://localhost:5173
```

The frontend works with or without the backend running — the landing
page health pill degrades gracefully to "Backend offline".

## 4. Run tests

**Backend** (uses an in-memory H2 database in PostgreSQL compatibility
mode — no local PostgreSQL needed for tests):

```bash
cd backend
mvn test
```

**Frontend:**

```bash
cd frontend
npm test
```

**Production build check:**

```bash
cd frontend
npm run build              # type-checks (tsc) then bundles into dist/
```

## Project structure

```
.
├── docs/                  # architecture, API reference, this guide
├── frontend/              # React + TypeScript + Vite + Tailwind
├── backend/               # Spring Boot 3 + Java 17 + Maven
├── .env.example           # all environment variables, documented
└── README.md
```

## Troubleshooting

| Symptom | Fix |
| --- | --- |
| Backend exits with a Flyway/connection error | Start PostgreSQL, or set `SPRING_DATASOURCE_URL` correctly |
| `npm install` fails on network | Check your registry/proxy; retry with `npm install --verbose` |
| Frontend health pill says "Backend offline" | Expected — start the backend, or ignore it during UI work |
| Port 5173 or 8080 already in use | `SERVER_PORT=<port>` for the backend; `npm run dev -- --port <port>` for Vite |
