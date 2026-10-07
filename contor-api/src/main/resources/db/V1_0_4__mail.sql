-- Connected mailboxes (Microsoft Graph). OAuth tokens are stored encrypted (*_enc, AES-256-GCM).
create sequence mail_account_seq start with 1 increment by 50;

create table "mail_account" (
    id bigint not null,
    owner_user_id bigint not null,
    provider text not null,
    email_address text not null,
    display_name text,
    access_token_enc text,
    refresh_token_enc text,
    token_expires_at timestamptz,
    delta_link text,
    last_synced_at timestamptz,
    status text not null default 'ACTIVE',
    created_at timestamptz not null,
    last_modified timestamptz,
    constraint pk_mail_account primary key (id),
    constraint fk_mail_account_app_user foreign key (owner_user_id) references "app_user" (id),
    constraint uq_mail_account_owner_provider_email unique (owner_user_id, provider, email_address),
    constraint ck_mail_account_provider check (provider in ('MICROSOFT_GRAPH')),
    constraint ck_mail_account_status check (status in ('ACTIVE', 'NEEDS_REAUTH', 'DISABLED'))
);
create index ix_mail_account_owner_user_id on "mail_account" (owner_user_id);

-- Synced email messages plus their AI processing state.
create sequence email_message_seq start with 1 increment by 50;

create table "email_message" (
    id bigint not null,
    account_id bigint not null,
    provider_message_id text not null,
    internet_message_id text,
    conversation_id text,
    folder text,
    from_address text,
    from_name text,
    to_recipients jsonb not null default '[]',
    cc_recipients jsonb not null default '[]',
    subject text,
    snippet text,
    body_text text,
    body_html text,
    headers jsonb not null default '[]',
    received_at timestamptz not null,
    is_read boolean not null default false,
    has_attachments boolean not null default false,
    processing_state text not null default 'NEW',
    processing_version int not null default 0,
    processing_error text,
    created_at timestamptz not null,
    last_modified timestamptz,
    constraint pk_email_message primary key (id),
    constraint fk_email_message_mail_account foreign key (account_id) references "mail_account" (id) on delete cascade,
    constraint uq_email_message_account_provider_message_id unique (account_id, provider_message_id),
    constraint ck_email_message_processing_state check (processing_state in ('NEW', 'QUEUED', 'PROCESSING', 'DONE', 'FAILED', 'SKIPPED'))
);
create index ix_email_message_account_received on "email_message" (account_id, received_at desc);
create index ix_email_message_conversation_id on "email_message" (conversation_id);
create index ix_email_message_from_address on "email_message" (from_address);
create index ix_email_message_processing_state on "email_message" (processing_state);
create index ix_email_message_subject_trgm on "email_message" using gin (subject gin_trgm_ops);

-- Embeddings per email and model (separate table so the embedding model can change).
-- Dimension 768 = nomic-embed-text; keep in sync with EmailEmbedding.DIMENSIONS.
create table "email_embedding" (
    email_id bigint not null,
    model text not null,
    embedding vector(768) not null,
    created_at timestamptz not null,
    constraint pk_email_embedding primary key (email_id, model),
    constraint fk_email_embedding_email_message foreign key (email_id) references "email_message" (id) on delete cascade
);
create index ix_email_embedding_hnsw on "email_embedding" using hnsw (embedding vector_cosine_ops);
