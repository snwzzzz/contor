-- Kinds of relations between records (graph edges), optionally restricted to source/target collections (null = any).
create sequence relation_type_seq start with 1 increment by 50;

create table "relation_type" (
    id bigint not null,
    slug text not null,
    name text not null,
    inverse_name text,
    from_collection_id bigint,
    to_collection_id bigint,
    directed boolean not null default true,
    attributes_schema jsonb not null default '{}',
    created_by text not null,
    created_at timestamptz not null,
    last_modified timestamptz,
    constraint pk_relation_type primary key (id),
    constraint uq_relation_type_slug unique (slug),
    constraint fk_relation_type_from_collection foreign key (from_collection_id) references "collection" (id),
    constraint fk_relation_type_to_collection foreign key (to_collection_id) references "collection" (id),
    constraint ck_relation_type_created_by check (created_by in ('USER', 'AI'))
);
create index ix_relation_type_from_collection_id on "relation_type" (from_collection_id);
create index ix_relation_type_to_collection_id on "relation_type" (to_collection_id);

-- Edges between two records, each addressed as (collection_id, record_id).
-- Compatibility with relation_type is validated in the service layer.
create sequence relation_seq start with 1 increment by 50;

create table "relation" (
    id bigint not null,
    relation_type_id bigint not null,
    from_collection_id bigint not null,
    from_record_id bigint not null,
    to_collection_id bigint not null,
    to_record_id bigint not null,
    attributes jsonb not null default '{}',
    source_email_id bigint,
    confidence numeric(5, 4),
    valid_from timestamptz,
    valid_to timestamptz,
    created_by text not null,
    created_at timestamptz not null,
    last_modified timestamptz,
    constraint pk_relation primary key (id),
    constraint fk_relation_relation_type foreign key (relation_type_id) references "relation_type" (id),
    constraint fk_relation_from_collection foreign key (from_collection_id) references "collection" (id),
    constraint fk_relation_to_collection foreign key (to_collection_id) references "collection" (id),
    constraint fk_relation_email_message foreign key (source_email_id) references "email_message" (id) on delete set null,
    constraint ck_relation_created_by check (created_by in ('USER', 'AI', 'RULE')),
    constraint ck_relation_confidence check (confidence between 0 and 1),
    constraint ck_relation_validity check (valid_to is null or valid_from is null or valid_to >= valid_from)
);
create index ix_relation_from on "relation" (from_collection_id, from_record_id);
create index ix_relation_to on "relation" (to_collection_id, to_record_id);
create index ix_relation_relation_type_id on "relation" (relation_type_id);
create index ix_relation_source_email_id on "relation" (source_email_id);
