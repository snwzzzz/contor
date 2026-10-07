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

## Decisions

See `docs/decisions/`.
