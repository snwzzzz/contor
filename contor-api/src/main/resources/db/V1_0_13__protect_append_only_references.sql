-- audit_log and collection_schema_change are append-only (trigger rejects UPDATE/DELETE). Their "on delete set null"
-- references to app_user would issue exactly such updates, so deleting an audited user must be prevented instead.
alter table "audit_log" drop constraint fk_audit_log_app_user;
alter table "audit_log" add constraint fk_audit_log_app_user foreign key (actor_user_id) references "app_user" (id);

alter table "collection_schema_change" drop constraint fk_collection_schema_change_app_user;
alter table "collection_schema_change" add constraint fk_collection_schema_change_app_user foreign key (executed_by) references "app_user" (id);
