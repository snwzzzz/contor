-- Saved queries over collections (query DSL as JSON). Storage only in phase 1; execution comes later.
create sequence saved_query_seq start with 1 increment by 50;

create table "saved_query" (
    id bigint not null,
    name text not null,
    description text,
    dsl jsonb not null,
    pinned boolean not null default false,
    created_at timestamptz not null,
    last_modified timestamptz,
    constraint pk_saved_query primary key (id)
);
create index ix_saved_query_pinned on "saved_query" (pinned);
