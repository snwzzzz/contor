-- Labels that can be put on emails, manually, by rules or by the AI.
create sequence label_seq start with 1 increment by 50;

create table "label" (
    id bigint not null,
    slug text not null,
    name text not null,
    color text not null,
    description text,
    created_by text not null,
    created_at timestamptz not null,
    last_modified timestamptz,
    constraint pk_label primary key (id),
    constraint uq_label_slug unique (slug),
    constraint ck_label_created_by check (created_by in ('USER', 'AI'))
);

-- Which label is on which email, and who put it there.
create table "email_label" (
    email_id bigint not null,
    label_id bigint not null,
    source text not null,
    confidence numeric(5, 4),
    created_at timestamptz not null,
    constraint pk_email_label primary key (email_id, label_id),
    constraint fk_email_label_email_message foreign key (email_id) references "email_message" (id) on delete cascade,
    constraint fk_email_label_label foreign key (label_id) references "label" (id) on delete cascade,
    constraint ck_email_label_source check (source in ('MANUAL', 'RULE', 'AI')),
    constraint ck_email_label_confidence check (confidence between 0 and 1)
);
create index ix_email_label_label_id on "email_label" (label_id);

-- Deterministic automation: conditions on incoming mail -> actions. Evaluated by priority (ascending).
create sequence rule_seq start with 1 increment by 50;

create table "rule" (
    id bigint not null,
    name text not null,
    enabled boolean not null default true,
    priority int not null default 100,
    conditions jsonb not null default '{}',
    actions jsonb not null default '[]',
    created_from_proposal_id bigint,
    created_at timestamptz not null,
    last_modified timestamptz,
    constraint pk_rule primary key (id)
);
create index ix_rule_enabled_priority on "rule" (enabled, priority);
create index ix_rule_created_from_proposal_id on "rule" (created_from_proposal_id);
