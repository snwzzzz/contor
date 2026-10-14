# 0004 – JPA for fixed tables, jOOQ for generated tables

**Status:** accepted (Phase 1, step 4)

## Context
The fixed schema (users, mail, labels, proposals, collection metadata, ...) is known at compile time and follows the
Appendix A entity/repository style. Collection tables are created at runtime (ADR 0003) and cannot be JPA entities.

## Decision
- Fixed tables: JPA entities + Spring Data repositories, schema by Flyway, Hibernate only validates.
- Generated tables: jOOQ DSL only, in `persistence/dynamic` (`CollectionTableDdl`, `DynamicRecordRepository`), without
  jOOQ code generation.
- Both share one transaction: jOOQ uses the transaction-aware `DataSource` that `JpaTransactionManager` binds to.

## Why
- JPA fits the fixed model (typed IDs, value objects, converters); it has no sensible way to map runtime tables.
- jOOQ builds safe SQL and DDL (quoted identifiers, bound values) from runtime metadata without string concatenation.

## Consequences
- jOOQ has no metadata for generated tables, so reads and writes always use typed fields from the field handlers'
  `ColumnSpec`s (otherwise JDBC types such as `java.sql.Date` leak through).
- JPA writes must be flushed (`saveAndFlush`) before jOOQ/JDBC reads in the same transaction can see them.
- Two persistence styles to know; the package split keeps them apart.
