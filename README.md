# contor

Personal, self-hosted management platform: dynamic collections (Deliveries, Orders, People, …) with relations between records, and later an Outlook inbox sync with a local Ollama model proposing actions.

> **Status: Phase 1, step 5 (backend API complete).** Backend: fixed schema, security (setup, sessions, CSRF, lockout, audit), the dynamic collection engine, labels, rules, relations, proposals (approve/reject with appliers for record proposals), saved queries and read-only mail. The frontend and TOTP 2FA follow in the next steps.

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

Edit `.env` and set at least `DB_PASSWORD` and `APP_ENCRYPTION_KEY`:

```bash
openssl rand -base64 32
```

`.env` is gitignored; never commit secrets. Keep `APP_ENCRYPTION_KEY` safe: without it, stored OAuth tokens and TOTP secrets cannot be decrypted. The backend refuses to start if the key is missing or not a 256-bit base64 key.

## Run

**1. Database**

```bash
docker compose --env-file .env -f infra/docker-compose.yml up -d --wait
```

**2. Backend** (http://127.0.0.1:8080). The backend reads its configuration from environment variables, so export `.env` first:

```bash
cd contor-api && set -a && . ../.env && set +a && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Alternatively, run without compose against a throwaway Testcontainers Postgres (still needs `APP_ENCRYPTION_KEY` exported):

```bash
cd contor-api && set -a && . ../.env && set +a && ./mvnw spring-boot:test-run
```

**3. Frontend** (http://localhost:3000). `/api/*` is proxied server-side to the backend (`CONTOR_API_URL`), so the browser only ever talks to one origin.

```bash
cd contor-web && npm install && npm run dev
```

Swagger UI for the contract (dev profile only, requires login): log in through the web app, then open http://localhost:8080/swagger-ui/index.html. Use the same host name (`localhost`) for both, since the session cookie is bound to the host name.

## First-run setup

There are no default credentials. On the first start with an empty database the backend prints a one-time **setup token** to its log:

```
==================================================================
 contor first-run setup: no user exists yet.
 ...
   <43-character token>
==================================================================
```

Use it once to create the owner account (the setup page in the web app follows in step 6; until then via `POST /api/setup`, see the API contract). The token lives only in memory: it is gone after use, and a restart issues a new one as long as no user exists. Once the owner exists, `/api/setup` is permanently disabled.

## Security overview

- Session cookie `CONTOR_SESSION` (HttpOnly, SameSite=Strict, `Secure` with `APP_SECURE_COOKIES=true`), stored in Postgres. 30 min idle timeout, 12 h absolute lifetime, at most 3 sessions per user (the oldest is ended), session id rotated on login.
- CSRF: the readable `XSRF-TOKEN` cookie must be echoed as `X-XSRF-TOKEN` header on every mutating request (`GET /api/auth/csrf` sets it). No CORS: the browser only talks to the Next.js origin.
- Passwords: Argon2id; 12–128 characters with lowercase, uppercase, digit and special character, not a common password.
- Brute force: 5 failed logins lock the account for 15 min (doubling on repeated lockouts, max 24 h); max 10 login requests per minute per IP. Unknown user, wrong password and locked account return the same error.
- Everything except login, CSRF bootstrap, setup (while pending) and `/actuator/health` requires a session.
- Audit log: logins, failures, lockouts, logouts, setup, password changes, collection schema changes, proposal decisions (later: 2FA).

## Database

- All fixed tables are created by Flyway migrations in `contor-api/src/main/resources/db` (`V1_0_N__description.sql`), applied automatically on backend startup. Hibernate only validates (`ddl-auto=validate`).
- Never change an applied migration; add a new one.
- Generated collection tables (`c_<slug>`) are not migrations; the collection engine creates them at runtime.
- `audit_log` and `collection_schema_change` are append-only (a trigger rejects `UPDATE`/`DELETE`).
- Columns ending in `_enc` are AES-256-GCM encrypted by the backend (`v1:` prefix = key version).
- Reset the local database (deletes all data):

```bash
docker compose --env-file .env -f infra/docker-compose.yml down -v
```

## Collections (dynamic data)

A collection (e.g. *Deliveries*) is metadata in `collection`/`collection_field` plus a generated table `c_<slug>` that holds its records. Field types: `TEXT`, `LONG_TEXT`, `NUMBER`, `MONEY`, `DATE`, `DATETIME`, `BOOLEAN`, `ENUM`, `STATUS`, `URL`, `EMAIL`, `PHONE`, `RELATION`, `FILE` (value shapes and options are documented in `api-spec/openapi.yaml`, schema `CollectionFieldType`).

- Creating a collection, adding and archiving fields is OWNER-only. Each change runs the generated DDL, bumps the collection's `schema_version`, and writes `collection_schema_change` and the audit log in one transaction.
- Archived fields are hidden and read-only; their column and data stay.
- Records: `GET/POST /api/collections/{id}/records`, `GET/PUT/DELETE .../records/{recordId}`. Filters: `?filter=eta:lt:2026-03-01&filter=carrier:eq:DHL&sort=-eta&page=0&size=25` (operators `eq ne lt lte gt gte contains empty notempty`).
- On first start contor seeds the system collections **People** and **Organizations** and the relation types `works_at` (People → Organizations, inverse `employs`) and `knows` (People ↔ People).
- The read-only database role `app_readonly` gets `SELECT` on every generated table (for user-written SQL in a later phase).

## Other resources (API overview)

| Area | Endpoints | Notes |
|---|---|---|
| Labels | `/api/labels` | CRUD; deleting a label removes it from all emails |
| Rules | `/api/rules` | CRUD; stored only (rule engine comes later) |
| Relations | `/api/relation-types`, `/api/relations?collectionId=&recordId=` | types are OWNER-only; relations are validated against the type's collections, both records must exist |
| Proposals | `/api/proposals`, `/api/proposals/count`, `POST /api/proposals/{id}/decision` | APPROVE (optionally with an edited payload) or REJECT; CREATE_RECORD / UPDATE_RECORD can be applied in phase 1 |
| Saved queries | `/api/saved-queries` | CRUD; stored only (execution comes later) |
| Mail | `/api/mail/accounts`, `/api/emails`, `/api/emails/{id}` | read-only; only the current user's mailboxes; `bodyHtml` is untrusted and must be rendered sandboxed |

The full contract with request/response shapes is `api-spec/openapi.yaml` (Swagger UI in the dev profile).

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
| `APP_ENCRYPTION_KEY` | backend | – (required) | base64 256-bit AES key for `*_enc` columns (`openssl rand -base64 32`) |
| `APP_ENCRYPTION_KEY_VERSION` | backend | `1` | version prefix of encrypted values; change only when rotating keys |
| `APP_SECURE_COOKIES` | backend | `false` | add `Secure` to session and CSRF cookies; set `true` when served over HTTPS |
| `APP_SESSION_IDLE_TIMEOUT` | backend | `30m` | session idle timeout |
| `APP_SESSION_ABSOLUTE_TIMEOUT` | backend | `12h` | maximum session lifetime regardless of activity |
| `OLLAMA_BASE_URL` | backend | `http://127.0.0.1:11434` | local Ollama (not called in phase 1; must stay on localhost) |
| `AI_MODEL_ROUTING` | backend | `qwen3:4b` | small model that classifies emails (next phase) |
| `AI_MODEL_EXTRACTION` | backend | `qwen3:14b` | model that drafts proposals (next phase) |
| `AI_MODEL_EMBEDDING` | backend | `nomic-embed-text` | embedding model, 768 dimensions (next phase) |
| `CONTOR_API_URL` | frontend (server only) | `http://127.0.0.1:8080` | target of the `/api/*` rewrite |

## Notes

- Only loopback is trusted as a proxy for `X-Forwarded-For` (the Next.js server). Do not put another proxy in front without adjusting `server.tomcat.remoteip.internal-proxies`.

- The backend JVM always runs in UTC (all timestamps are `timestamptz`). This also avoids PostgreSQL 18 rejecting legacy JVM zone names such as `US/Pacific`.
- Ollama is not part of compose; it runs natively for GPU access and must stay on localhost.
