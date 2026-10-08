# contor

Personal, self-hosted management platform: dynamic collections (Deliveries, Orders, People, …) with relations between records, an Outlook inbox sync, and a local AI assistant (Ollama) that analyzes the inbox, creates matching categories and keeps its own calendar and to-do list.

> **Status: Phase 2 (local AI).** Backend: security, collection engine, labels, rules, relations, proposals, saved queries, Outlook mail sync, and the AI pipeline (Ollama client, permissions, inbox analysis runs). Frontend: setup/login, app shell, inbox, AI setup wizard, proposals review, calendar and to-dos. The collection UI and TOTP 2FA follow. Plan: `docs/ai-plan.md`.

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
- [Ollama](https://ollama.com) running natively on the host (bound to localhost, the default) for the AI assistant; see *Local AI*

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

**2. Backend** (http://127.0.0.1:8080). The backend reads the repo-root `.env` itself when started from `contor-api` (IntelliJ run button or `./mvnw spring-boot:run`); values must not be quoted, and real environment variables take precedence. Exporting it first also works:

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

Production build: `CONTOR_API_URL` is baked into the `/api` rewrite at **build** time (Next.js resolves rewrites when building), so set it for `npm run build` as well as for `npm run start`:

```bash
cd contor-web && CONTOR_API_URL=http://127.0.0.1:8080 npm run build && CONTOR_API_URL=http://127.0.0.1:8080 npm run start
```

On first start open http://localhost:3000: without a user you are sent to **/setup** (token from the backend log, see below), afterwards to **/login**.

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


### Dev account

With the `dev` profile the backend keeps a login ready: set `DEV_ACCOUNT_USERNAME`, `DEV_ACCOUNT_PASSWORD` (same password rules) and optionally `DEV_ACCOUNT_EMAIL` in `.env`. On every start it is created as OWNER if missing, otherwise its password is reset to the configured one and any lockout cleared. First-run setup is then skipped, and the web app signs in as this account by itself: the login page asks the backend (`GET /api/auth/dev-login`, server-side) and calls `POST /api/auth/dev-login`, which exists only with the `dev` profile and answers loopback requests only. Log out to see the login page again (it signs you straight back in). Never set these outside local development.
## Security overview

- Session cookie `CONTOR_SESSION` (HttpOnly, SameSite=Strict, `Secure` with `APP_SECURE_COOKIES=true`), stored in Postgres. 30 min idle timeout, 12 h absolute lifetime, at most 3 sessions per user (the oldest is ended), session id rotated on login.
- CSRF: the readable `XSRF-TOKEN` cookie must be echoed as `X-XSRF-TOKEN` header on every mutating request (`GET /api/auth/csrf` sets it). No CORS: the browser only talks to the Next.js origin.
- Passwords: Argon2id; 12–128 characters with lowercase, uppercase, digit and special character, not a common password.
- Brute force: 5 failed logins lock the account for 15 min (doubling on repeated lockouts, max 24 h); max 10 login requests per minute per IP. Unknown user, wrong password and locked account return the same error.
- Everything except login, CSRF bootstrap, setup (while pending) and `/actuator/health` requires a session.
- Audit log: logins, failures, lockouts, logouts, setup, password changes, collection schema changes, proposal decisions and AI auto-applied changes, AI permission changes, analysis starts/cancels (later: 2FA).

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
- On first start contor seeds the system collections **People**, **Organizations**, **Events** (the assistant's calendar) and **Tasks** (its to-do list) and the relation types `works_at` (People → Organizations, inverse `employs`), `knows` (People ↔ People) and `attends` (People → Events).
- The read-only database role `app_readonly` gets `SELECT` on every generated table (for user-written SQL in a later phase).

## Local AI (Ollama)

The assistant runs entirely on this computer. Model choice for an RTX 3060 (12 GB): **`qwen3.5:9b`** for discovery and
extraction (one resident model, reliable JSON, German and English) plus `nomic-embed-text`; details and alternatives in
`docs/decisions/0007-local-ai-with-ollama.md`.

```bash
ollama pull qwen3.5:9b
```

```bash
ollama pull nomic-embed-text
```

(Or use the *Download missing models* button in the setup wizard.) Then open the app and click **Set up AI** in the top
bar (or Settings → AI Models). The wizard walks through:

1. **Welcome** – what the assistant does; nothing leaves the machine.
2. **Model** – checks that Ollama runs and the models are installed, downloads missing ones.
3. **Permissions** – read the mailboxes (off until you allow it), how far back (30/90/365 days or everything), and
   *Off / Ask me / Automatic* for new categories, contacts & records, calendar and to-dos.
4. **Start** – saves the permissions and starts the analysis.
5. **Analysis** – live progress. With categories on *Ask me* the run pauses after discovery so you can create or skip
   each suggested category (e.g. *Job applications*, *Orders*), then continues email by email.

- *Ask me* stores proposals: extracted people, organizations and category records wait on **`/findings`** (keep/dismiss),
  actions such as calendar entries, to-dos and new categories on **`/proposals`** (approve/reject). *Automatic* applies
  them directly (audited; if applying fails the proposal waits for review instead).
- The assistant's own calendar and to-do list are the system collections **Events** (`/calendar`) and **Tasks**
  (`/tasks`); people and organizations go to **People**/**Organizations**; duplicates are matched by match keys or title.
- Expect ~12 s per email on a 3060 (the first call after a cold start ~20–50 s): the first analysis of a busy inbox runs
  for hours in the background and can be stopped at any time. Afterwards new mail is processed every 2 minutes.
- Permissions can be changed any time under Settings → Autonomy; *Analyze inbox again* lives under Settings → AI Models.

| Endpoint | Purpose |
|---|---|
| `GET /api/ai/status`, `POST /api/ai/models/pull` | Ollama reachable? models installed? download missing (OWNER) |
| `GET/PUT /api/ai/settings` | the current user's permissions (PUT OWNER; `completeOnboarding` ends the wizard) |
| `POST /api/ai/analysis`, `GET /api/ai/analysis/latest`, `POST /api/ai/analysis/{id}/continue\|cancel` | inbox analysis runs (mutations OWNER) |

## Other resources (API overview)

| Area | Endpoints | Notes |
|---|---|---|
| Labels | `/api/labels` | CRUD; deleting a label removes it from all emails |
| Rules | `/api/rules` | CRUD; stored only (rule engine comes later) |
| Relations | `/api/relation-types`, `/api/relations?collectionId=&recordId=` | types are OWNER-only; relations are validated against the type's collections, both records must exist |
| Proposals | `/api/proposals`, `/api/proposals/count`, `POST /api/proposals/{id}/decision` | APPROVE (optionally with an edited payload) or REJECT; CREATE_RECORD, UPDATE_RECORD and CREATE_COLLECTION (OWNER) can be applied. `?group=FINDINGS` = extracted records (shown on `/findings`), `?group=ACTIONS` = everything the AI proposes to do, incl. calendar entries and to-dos (shown on `/proposals`) |
| Saved queries | `/api/saved-queries` | CRUD; stored only (execution comes later) |
| Mail | `/api/mail/accounts[/*]`, `/api/emails`, `/api/emails/{id}` | Microsoft Graph sync: connect, callback (always forwards the browser to `/settings/accounts`, with `?connected=true` or `?mailError=`), sync, disconnect, status; disconnected accounts can be reconnected from the Accounts page; mailbox profile photo (`/api/mail/accounts/{id}/photo`, fetched on the first sync after connecting); emails scoped to current user, inbox filterable per account |
| Senders | `/api/senders/logos[/{domain}]`, `/api/senders/contact-photos[/{id}]` | Sender pictures: Outlook contact photos (read by the mail sync, daily; needs `Contacts.Read`) and sender domain logos (fetched by the backend from `https://<domain>` — public hosts only, raster images ≤ 256 KB — cached 30 days, misses 7 days) |

The full contract with request/response shapes is `api-spec/openapi.yaml` (Swagger UI in the dev profile).

## Code generation (contract first)

`api-spec/openapi.yaml` is the only place the API is defined. Generated code is never edited by hand and never committed.

| Side | Tool | Output | Command |
|---|---|---|---|
| Backend | openapi-generator-maven-plugin (`spring`, interface only) | `contor-api/target/generated-sources/openapi` (`com.example.contor.api`, `.api.model`) | runs on every Maven build, e.g. `./mvnw compile` |
| Frontend | orval (TanStack Query hooks + fetch) | `contor-web/src/lib/api/generated/` | `npm run generate:api` (also runs before `dev`, `build`, `test`, `typecheck`) |

IntelliJ: if `com.example.contor.api` shows as unresolved, run *Maven → Generate Sources and Update Folders* (or `./mvnw generate-sources`) and reload the Maven project; `build-helper-maven-plugin` registers `target/generated-sources/openapi/src/main/java` as a source root.

To change the API: edit the spec, regenerate both sides, then implement. See `docs/decisions/0001-frontend-api-client-orval.md` for the generator choice.

## Tests

```bash
# Backend: unit, slice and integration tests (Docker must be running)
cd contor-api && ./mvnw test

# Frontend: unit/component tests, lint, type check
cd contor-web && npm test && npm run lint && npm run typecheck

# Frontend: end-to-end against the real backend (Docker required). Starts contor-api on port 18080 with a fresh
# Testcontainers Postgres, runs first-run setup through the UI, then the auth and shell flows on port 3100.
cd contor-web && npx playwright install chromium && npm run test:e2e

# Same against a production build (strict CSP without dev allowances). Builds into contor-web/.next-e2e,
# so your own build in .next (and its CONTOR_API_URL) is never replaced.
cd contor-web && npm run test:e2e:prod

# Screenshots of the main screens in light and dark mode -> contor-web/e2e/.tmp/screenshots/ (for design work)
cd contor-web && npm run screenshots
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
| `OLLAMA_BASE_URL` | backend | `http://127.0.0.1:11434` | local Ollama; must be localhost/127.0.0.1/::1 (the backend refuses to start otherwise) |
| `OLLAMA_TIMEOUT` | backend | `5m` | read timeout of one model call |
| `OLLAMA_NUM_CTX` | backend | `16384` | context window in tokens (more needs more VRAM; changing it reloads the model) |
| `OLLAMA_KEEP_ALIVE` | backend | `30m` | how long Ollama keeps the model loaded after a call |
| `AI_MODEL_ROUTING` | backend | `qwen3.5:9b` | model for category discovery |
| `AI_MODEL_EXTRACTION` | backend | `qwen3.5:9b` | model that reads each email and drafts records (same model = no swapping on 12 GB) |
| `AI_MODEL_EMBEDDING` | backend | `nomic-embed-text` | embedding model, 768 dimensions |
| `AI_TIMEZONE` | backend | `Europe/Berlin` | time zone local times in emails are read in ("Friday 9:00") |
| `AI_PROCESSING_ENABLED` | backend | `true` | analyze new mail in the background after the AI setup |
| `AI_PROCESSING_INTERVAL` | backend | `2m` | pause between background passes |
| `AI_PROCESSING_BATCH_SIZE` | backend | `20` | emails per user and pass |
| `MSGRAPH_CLIENT_ID` | backend | – | Azure app registration client ID |
| `MSGRAPH_CLIENT_SECRET` | backend | – | Azure app registration client secret |
| `MSGRAPH_TENANT_ID` | backend | `common` | Entra ID tenant ID (`common` for work/school + personal) |
| `DEV_ACCOUNT_USERNAME`, `DEV_ACCOUNT_PASSWORD`, `DEV_ACCOUNT_EMAIL` | backend (dev profile) | empty | Login the dev backend creates/resets on start (see *Dev account*) |
| `APP_WEB_URL` | backend | `http://localhost:3000` | Web app URL the browser is forwarded to after the Microsoft OAuth callback (`/settings/accounts?connected=true`) |
| `MSGRAPH_REDIRECT_URI` | backend | `http://localhost:8080/api/mail/accounts/callback` | OAuth2 callback URL matching app registration |
| `MSGRAPH_SCOPES` | backend | `Mail.Read,User.Read,Contacts.Read,offline_access` | Microsoft Graph API permissions, comma- or space-separated; keep `offline_access` (refresh token); `Contacts.Read` gives sender photos from your Outlook contacts |
| `MAIL_SYNC_INTERVAL` | backend | `5m` | Background email delta synchronization interval |
| `CONTOR_API_URL` | frontend (server only) | `http://127.0.0.1:8080` | backend URL: target of the `/api/*` rewrite (fixed at build time) and of server-side calls |
| `CONTOR_HTTPS` | frontend | `false` | `true` adds `Strict-Transport-Security` (only when served over HTTPS) |

## Frontend structure and redesigns

The UI is built so a visual redesign does not touch pages or data logic:

| Layer | Where | Change it for… |
|---|---|---|
| Design tokens (colors, radius, fonts; light + dark) | `contor-web/src/styles/tokens.css` | palette, typography, roundness |
| Primitives (shadcn/ui, owned) | `src/components/ui/` | how a button/input/dialog looks in general |
| App components | `src/components/app/` (`AppButton`, `FormField`, `PageHeader`, `EmptyState`, `ErrorMessage`, `AuthCard`, forms) | app-wide patterns |
| Layout | `src/components/layout/` (`AppShell`, `AppSidebar`, `TopBar`, `CommandPalette`, `UserMenu`) | shell and navigation |
| Pages | `src/app/` | composition only: no styling decisions, no `components/ui` imports (ESLint enforces) |

Navigation entries live in `src/lib/navigation.ts` (shared by sidebar and command palette). `npm run screenshots` captures before/after images.

The sidebar can be resized (drag its right edge, or focus the edge and use the arrow keys; double-click collapses it) and collections can be reordered (drag them, or focus a collection's grip and press Alt+↑/↓). Both are per-browser preferences in `localStorage` (`contor.sidebar.width`, `contor.sidebar.collectionOrder`), never stored on the server. The theme stays black and white; color comes only from `success`/`info`/`warning` and the per-collection tints (`--tint-1..6`: same lightness and chroma, only the hue changes), all in `src/styles/tokens.css`. Scrollbars are thin everywhere (`globals.css`).

## Notes

- Only loopback is trusted as a proxy for `X-Forwarded-For` (the Next.js server). Do not put another proxy in front without adjusting `server.tomcat.remoteip.internal-proxies`.

- The backend JVM always runs in UTC (all timestamps are `timestamptz`). This also avoids PostgreSQL 18 rejecting legacy JVM zone names such as `US/Pacific`.
- Ollama is not part of compose; it runs natively for GPU access and must stay on localhost (enforced by the backend).
