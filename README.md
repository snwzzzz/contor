# contor

Personal, self-hosted management platform: dynamic collections (Deliveries, Orders, People, …) with relations between records, and later an Outlook inbox sync with a local Ollama model proposing actions.

> **Status: Phase 1, step 1 (foundation skeleton).** Backend and frontend start, test tooling and code generation are wired against a minimal API contract. Database schema, security, collections and the app shell follow in the next steps.

## Repository layout

| Path | What |
|---|---|
| `api-spec/openapi.yaml` | API contract, single source of truth for backend and frontend |
| `contor-api/` | Spring Boot 4.1 backend (Java 21, Maven wrapper) |
| `contor-web/` | Next.js 16 frontend (App Router, TypeScript, Tailwind) |
| `infra/docker-compose.yml` | PostgreSQL 18 + pgvector, bound to `127.0.0.1` |
| `docs/` | architecture and decision records |
| `CLAUDE.md` | working rules and conventions |

## Prerequisites

- Java 21 (no Maven install needed, use `./mvnw`)
- Node.js 24+ and npm 11+
- Docker (Docker Desktop or compatible): Postgres for dev, Testcontainers for backend tests
- Later phases: Ollama running natively on the host (it must stay bound to localhost)

Optional, faster backend tests: let Testcontainers reuse the Postgres container between runs:

```bash
echo "testcontainers.reuse.enable=true" >> ~/.testcontainers.properties
```

## First-time setup

```bash
cp .env.example .env
```

Edit `.env` and set at least `DB_PASSWORD`. `.env` is gitignored; never commit secrets.

## Run

**1. Database**

```bash
docker compose --env-file .env -f infra/docker-compose.yml up -d --wait
```

**2. Backend** (http://127.0.0.1:8080). The backend reads its configuration from environment variables, so export `.env` first:

```bash
cd contor-api && set -a && . ../.env && set +a && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Alternatively, run without compose against a throwaway Testcontainers Postgres:

```bash
cd contor-api && ./mvnw spring-boot:test-run
```

**3. Frontend** (http://localhost:3000). `/api/*` is proxied server-side to the backend (`CONTOR_API_URL`), so the browser only ever talks to one origin.

```bash
cd contor-web && npm install && npm run dev
```

Swagger UI for the contract (dev profile only): http://127.0.0.1:8080/swagger-ui/index.html

## Code generation (contract first)

`api-spec/openapi.yaml` is the only place the API is defined. Generated code is never edited by hand and never committed.

| Side | Tool | Output | Command |
|---|---|---|---|
| Backend | openapi-generator-maven-plugin (`spring`, interface only) | `contor-api/target/generated-sources/openapi` (`com.example.contor.api`, `.api.model`) | runs on every Maven build, e.g. `./mvnw compile` |
| Frontend | orval (TanStack Query hooks + fetch) | `contor-web/src/lib/api/generated/` | `npm run generate:api` (also runs before `dev`, `build`, `test`, `typecheck`) |

To change the API: edit the spec, regenerate both sides, then implement. See `docs/decisions/0001-frontend-api-client-orval.md` for the generator choice.

## Tests

```bash
# Backend: unit, slice and integration tests (Docker must be running)
cd contor-api && ./mvnw test

# Frontend: unit/component tests, lint, type check
cd contor-web && npm test && npm run lint && npm run typecheck

# Frontend: end-to-end (starts its own dev server on port 3100)
cd contor-web && npx playwright install chromium && npm run test:e2e
```

## Environment variables

All variables are listed in `.env.example`.

| Variable | Used by | Default | Purpose |
|---|---|---|---|
| `DB_NAME` | compose | `contor` | database name |
| `DB_USERNAME` | compose, backend | `contor` | database user |
| `DB_PASSWORD` | compose, backend | – (required) | database password |
| `DB_PORT` | compose | `5432` | host port, bound to 127.0.0.1 |
| `DB_URL` | backend | `jdbc:postgresql://127.0.0.1:5432/contor` | JDBC URL |
| `APP_BIND_ADDRESS` | backend | `127.0.0.1` | interface Spring binds to |
| `APP_PORT` | backend | `8080` | backend port |
| `CONTOR_API_URL` | frontend (server only) | `http://127.0.0.1:8080` | target of the `/api/*` rewrite |

## Notes

- The backend JVM always runs in UTC (all timestamps are `timestamptz`). This also avoids PostgreSQL 18 rejecting legacy JVM zone names such as `US/Pacific`.
- Ollama is not part of compose; it runs natively for GPU access and must stay on localhost.
