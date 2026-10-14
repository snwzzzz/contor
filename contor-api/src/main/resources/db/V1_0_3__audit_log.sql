-- Append-only trail of security-relevant and data-changing actions by users, the AI and the system.
-- Records of generated collection tables are referenced as (collection_id, entity_id).
create sequence audit_log_seq start with 1 increment by 50;

create table "audit_log" (
    id bigint not null,
    occurred_at timestamptz not null,
    actor_type text not null,
    actor_user_id bigint,
    action text not null,
    entity_type text,
    collection_id bigint,
    entity_id bigint,
    before jsonb,
    after jsonb,
    ip_address text,
    user_agent text,
    constraint pk_audit_log primary key (id),
    constraint fk_audit_log_app_user foreign key (actor_user_id) references "app_user" (id) on delete set null,
    constraint ck_audit_log_actor_type check (actor_type in ('USER', 'AI', 'SYSTEM'))
);
create index ix_audit_log_occurred_at on "audit_log" (occurred_at desc);
create index ix_audit_log_actor_user_id on "audit_log" (actor_user_id);
create index ix_audit_log_action on "audit_log" (action);
create index ix_audit_log_entity on "audit_log" (entity_type, collection_id, entity_id);

create trigger trg_audit_log_append_only
    before update or delete on "audit_log"
    for each row execute function forbid_update_delete();
