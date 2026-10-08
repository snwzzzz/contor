-- What the local AI may do for a user, set in the AI setup wizard. One row per user; no row = defaults (no mail access).
-- Levels: OFF = never touch, SUGGEST = proposals for review, AUTO = apply directly.
create sequence ai_settings_seq start with 1 increment by 50;

create table "ai_settings" (
    id bigint not null,
    user_id bigint not null,
    mail_access boolean not null default false,
    history_days int not null default 90,
    categories text not null default 'SUGGEST',
    records text not null default 'SUGGEST',
    calendar text not null default 'SUGGEST',
    tasks text not null default 'SUGGEST',
    onboarded_at timestamptz,
    created_at timestamptz not null,
    last_modified timestamptz,
    constraint pk_ai_settings primary key (id),
    constraint fk_ai_settings_app_user foreign key (user_id) references "app_user" (id) on delete cascade,
    constraint uq_ai_settings_user_id unique (user_id),
    constraint ck_ai_settings_history_days check (history_days between 0 and 3650),
    constraint ck_ai_settings_categories check (categories in ('OFF', 'SUGGEST', 'AUTO')),
    constraint ck_ai_settings_records check (records in ('OFF', 'SUGGEST', 'AUTO')),
    constraint ck_ai_settings_calendar check (calendar in ('OFF', 'SUGGEST', 'AUTO')),
    constraint ck_ai_settings_tasks check (tasks in ('OFF', 'SUGGEST', 'AUTO'))
);

-- One analysis of a user's inbox: discovery of categories, then extraction email by email. Progress counters are
-- updated while the run is active so the UI can show them.
create sequence ai_analysis_run_seq start with 1 increment by 50;

create table "ai_analysis_run" (
    id bigint not null,
    user_id bigint not null,
    status text not null default 'QUEUED',
    since timestamptz,
    total_emails int not null default 0,
    processed_emails int not null default 0,
    failed_emails int not null default 0,
    proposals_created int not null default 0,
    auto_applied int not null default 0,
    categories_discovered int not null default 0,
    error text,
    started_at timestamptz,
    finished_at timestamptz,
    created_at timestamptz not null,
    last_modified timestamptz,
    constraint pk_ai_analysis_run primary key (id),
    constraint fk_ai_analysis_run_app_user foreign key (user_id) references "app_user" (id) on delete cascade,
    constraint ck_ai_analysis_run_status check (status in ('QUEUED', 'DISCOVERING', 'AWAITING_REVIEW', 'EXTRACTING',
                                                           'COMPLETED', 'FAILED', 'CANCELLED')),
    constraint ck_ai_analysis_run_counters check (total_emails >= 0 and processed_emails >= 0 and failed_emails >= 0
                                                  and proposals_created >= 0 and auto_applied >= 0
                                                  and categories_discovered >= 0)
);
create index ix_ai_analysis_run_user_id_created_at on "ai_analysis_run" (user_id, created_at);
create index ix_ai_analysis_run_status on "ai_analysis_run" (status);

-- The worker picks a user's unprocessed emails in order.
create index ix_email_message_processing_state_received_at on "email_message" (processing_state, received_at);
