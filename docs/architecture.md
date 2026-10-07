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

## Decisions

See `docs/decisions/`.
