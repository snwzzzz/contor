-- Extensions used across the schema: pgvector for email embeddings, pg_trgm for fuzzy subject search.
create extension if not exists vector;
create extension if not exists pg_trgm;

-- Shared trigger function that makes a table append-only (history and audit tables).
create function forbid_update_delete() returns trigger
    language plpgsql as
$$
begin
    raise exception 'table % is append-only', tg_table_name;
end;
$$;
