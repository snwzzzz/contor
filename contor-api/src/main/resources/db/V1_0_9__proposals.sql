-- Actions proposed by the AI (or rules), decided by the user or auto-applied depending on autonomy settings.
create sequence proposal_seq start with 1 increment by 50;

create table "proposal" (
    id bigint not null,
    kind text not null,
    status text not null default 'PENDING',
    payload jsonb not null,
    final_payload jsonb,
    source_email_id bigint,
    confidence numeric(5, 4),
    reasoning text,
    model text,
    prompt_version text,
    schema_version int,
    decided_at timestamptz,
    decided_by bigint,
    created_at timestamptz not null,
    last_modified timestamptz,
    constraint pk_proposal primary key (id),
    constraint fk_proposal_email_message foreign key (source_email_id) references "email_message" (id) on delete set null,
    constraint fk_proposal_app_user foreign key (decided_by) references "app_user" (id) on delete set null,
    constraint ck_proposal_kind check (kind in ('CREATE_RECORD', 'UPDATE_RECORD', 'CREATE_RELATION', 'SET_LABEL', 'ARCHIVE_EMAIL',
                                                'DRAFT_REPLY', 'CREATE_COLLECTION', 'ADD_FIELD', 'CREATE_RULE', 'CREATE_LABEL')),
    constraint ck_proposal_status check (status in ('PENDING', 'APPROVED', 'EDITED_AND_APPROVED', 'REJECTED', 'AUTO_APPLIED',
                                                    'UNDONE', 'FAILED')),
    constraint ck_proposal_confidence check (confidence between 0 and 1)
);
create index ix_proposal_status_created_at on "proposal" (status, created_at);
create index ix_proposal_source_email_id on "proposal" (source_email_id);
create index ix_proposal_decided_by on "proposal" (decided_by);

alter table "rule" add constraint fk_rule_proposal foreign key (created_from_proposal_id) references "proposal" (id) on delete set null;

-- How autonomously the AI may act, per proposal kind and scope. No row means SUGGEST.
create sequence autonomy_setting_seq start with 1 increment by 50;

create table "autonomy_setting" (
    id bigint not null,
    action_kind text not null,
    scope_type text not null,
    scope_id bigint,
    level text not null default 'SUGGEST',
    created_at timestamptz not null,
    last_modified timestamptz,
    constraint pk_autonomy_setting primary key (id),
    constraint uq_autonomy_setting_kind_scope unique nulls not distinct (action_kind, scope_type, scope_id),
    constraint ck_autonomy_setting_action_kind check (action_kind in ('CREATE_RECORD', 'UPDATE_RECORD', 'CREATE_RELATION', 'SET_LABEL',
                                                                      'ARCHIVE_EMAIL', 'DRAFT_REPLY', 'CREATE_COLLECTION', 'ADD_FIELD',
                                                                      'CREATE_RULE', 'CREATE_LABEL')),
    constraint ck_autonomy_setting_scope_type check (scope_type in ('GLOBAL', 'COLLECTION', 'LABEL', 'RULE')),
    constraint ck_autonomy_setting_scope_id check ((scope_type = 'GLOBAL') = (scope_id is null)),
    constraint ck_autonomy_setting_level check (level in ('SUGGEST', 'AUTO_WITH_UNDO', 'AUTO'))
);
