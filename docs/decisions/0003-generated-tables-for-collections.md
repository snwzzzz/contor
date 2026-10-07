# 0003 – One generated table per collection (not JSONB documents or EAV)

**Status:** accepted (Phase 1, step 4)

## Context
Collections and their fields are defined at runtime by the user or the AI. Records must be filterable, sortable,
relatable to each other (foreign keys) and later queryable with user-written, read-only SQL. Options: one real table per
collection, one JSONB document per record in a shared table, or entity-attribute-value rows.

## Decision
Each collection gets its own table `c_<slug>` with real, typed columns per field, created and altered at runtime with
jOOQ DDL. The metadata tables (`collection`, `collection_field`) are the source of truth; every DDL statement is recorded in
`collection_schema_change`. A `jsonb` column `extra` per table holds values for fields that do not exist yet.

## Why
- Real types and constraints: numeric/date comparisons, unique indexes and foreign keys for RELATION fields are enforced by
  PostgreSQL, not by application code.
- Plain SQL stays readable (`select * from c_deliveries where eta < now()`), which the planned read-only SQL mode and the
  `app_readonly` role rely on; EAV would make every query a pivot, JSONB would lose types and FKs.
- PostgreSQL's transactional DDL lets metadata, DDL and history commit or roll back together.

## Consequences
- Identifiers come only from validated slugs (`Slug` value object, reserved-word list, `DynamicNames` ≤ 63 chars); all SQL
  is built with the jOOQ DSL.
- Field removal is an archive flag in this phase (no physical `DROP COLUMN`), so history and data are preserved.
- Record ids are only unique per table; every external reference is (collection_id, record_id).
- Schema changes take table locks; fine for a single-user app.
