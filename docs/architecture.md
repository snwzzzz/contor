# contor architecture

> Living document. Filled in step by step during Phase 1; final pass in step 9.

## Runtime topology

```
Browser ──▶ Next.js (contor-web, :3000) ──rewrite /api/*──▶ Spring Boot (contor-api, 127.0.0.1:8080) ──▶ PostgreSQL 18 + pgvector (127.0.0.1:5432)
                                                                        └──(later)──▶ Ollama (localhost, native)
```

The browser talks to one origin only. No CORS.

## Contract first

`api-spec/openapi.yaml` → openapi-generator (backend `XApi` interfaces + models in `com.example.contor.api[.model]`) and orval (frontend hooks + models). Controllers implement the generated interfaces; generated models replace hand-written commands/DTOs (`NewXCommand`, `UpdateXCommand`, `XDto`).

## Backend layers

```
Generated XApi ◀─implements─ Controller ──(NewXCommand)──▶ Service ──▶ Repository ──▶ Entity
                                   ◀──────(XDto)──────── XMapper.toDto(entity)
```

Fixed tables: JPA + Flyway. Generated collection tables: jOOQ only (`persistence/dynamic`).

## Data model (fixed schema, Flyway)

| Area | Tables | Notes |
|---|---|---|
| Users & security | `app_user`, `recovery_code`, `spring_session*` | TOTP secret encrypted; recovery codes hashed |
| Audit | `audit_log` | append-only (trigger), records referenced as (collection_id, entity_id) |
| Mail | `mail_account`, `email_message`, `email_embedding` | tokens encrypted; embeddings per (email, model), `vector(768)` + HNSW; trigram index on subject |
| Labels & rules | `label`, `email_label`, `rule` | `email_label` composite key |
| Collections | `collection`, `collection_field`, `collection_schema_change` | metadata for generated `c_<slug>` tables; schema history append-only |
| Record links | `record_source` | (collection_id, record_id) ↔ email |
| Graph | `relation_type`, `relation` | edges between (collection_id, record_id) pairs |
| AI | `proposal`, `autonomy_setting` | no autonomy row = SUGGEST |
| Queries | `saved_query` | storage only in phase 1 |

ID strategy: aggregates use `bigint` + `<table>_seq` (increment 50) mapped as nested `@EmbeddedId` records; detail tables (`recovery_code`, `collection_schema_change`, `record_source`) use identity columns; `email_label` and `email_embedding` use composite keys. Timestamps are `timestamptz`, the JVM runs in UTC.

## Collection engine

```
CollectionController (OWNER) ──▶ CollectionSchemaService ──▶ collection / collection_field (JPA)
                                        │                 ──▶ CollectionTableDdl (jOOQ DDL: create table, add column, FK, unique index, grant)
                                        │                 ──▶ collection_schema_change (+ schema_version++) and audit_log
                                        └── FieldTypeRegistry ─▶ FieldTypeHandler per type (columns, validation, conversion)
RecordController ──▶ RecordService ──▶ DynamicRecordRepository (jOOQ CRUD on c_<slug>, typed fields)
                           └── FieldTypeRegistry (validate, toDb/fromDb, filter parsing, STATUS transitions)
```

All of a schema change (metadata, DDL, history, audit) is one PostgreSQL transaction, so it commits or rolls back as a whole.
Generated tables have the system columns `id` (identity), `title`, `status`, `extra jsonb` (values for fields that do not
exist yet), `created_at`, `last_modified`, `created_by`, plus one or more columns per field (MONEY: `<f>_amount`,
`<f>_currency`; RELATION: `bigint` FK to the target table, `on delete set null`, indexed). Records are referenced from outside
as (collection_id, record_id).

## Proposals

```
(next phase) ExtractionService ──▶ proposal (PENDING, payload, source email, confidence, model)
ProposalController ──▶ ProposalService.decideProposal
                         ├─ REJECT  → REJECTED
                         └─ APPROVE → ProposalApplierRegistry → ProposalApplier(kind).apply(finalPayload)
                                        CreateRecordApplier / UpdateRecordApplier → RecordService (+ record_source)
                         → decided_at/by, audit PROPOSAL_APPROVED/REJECTED   (one transaction; failure keeps PENDING)
```

Autonomy settings (`autonomy_setting`, default SUGGEST) are stored but not evaluated until the AI pipeline exists.

## Authentication flow

```
Browser                      Next.js (:3000)                 Spring (127.0.0.1:8080)
  │ GET /api/auth/csrf ───────▶ rewrite ─────────────────────▶ CsrfFilter (SPA mode) sets XSRF-TOKEN cookie
  │ POST /api/auth/login ─────▶ rewrite (+X-Forwarded-For) ──▶ CsrfFilter checks X-XSRF-TOKEN
  │   {username,password}                                      AuthService: rate limit (IP) → AuthenticationManager
  │                                                            (Argon2id, lock check) → LoginAttemptService
  │                                                            → SessionAuthenticationStrategy (≤3 sessions, new id,
  │                                                              fresh CSRF token) → context saved in spring_session
  │ ◀── 200 CurrentUserDto, Set-Cookie CONTOR_SESSION (HttpOnly, SameSite=Strict), XSRF-TOKEN
```

First run: `SetupTokenInitializer` prints a one-time token while `app_user` is empty; `POST /api/setup` (permitted only while setup is pending) consumes it and creates the OWNER.

## Frontend

```
src/proxy.ts            CSP nonce + optimistic auth redirect (every page request)
src/app/(auth)/…        /login, /setup  (server components check setup status against the backend, then render forms)
src/app/(app)/…         AppShell (sidebar, top bar, command palette) + pages; pages only compose components
src/components/app      owned composites (AppButton, FormField, PageHeader, EmptyState, ErrorMessage, auth forms)
src/components/layout   shell pieces
src/components/ui       shadcn primitives (owned)
src/styles/tokens.css   design tokens (light/dark)
src/lib/api             generated orval client + fetcher (CSRF, ApiError) + QueryClient (401 → login, mutation errors → toast)
```

Data is fetched client-side with TanStack Query through the same-origin `/api` rewrite; the session cookie is HttpOnly and never touched by JavaScript.

## Decisions

See `docs/decisions/`.
