-- Records and proposals can come from an item of an external API (an app) instead of an email (ADR 0012): a record's
-- source is either an email or an API item, never both. MAP_API_ENDPOINT proposes how an endpoint's items become
-- records. The assistant's permission for records from apps is its own setting (default: suggestions).

alter table "record_source" alter column email_id drop not null;
alter table "record_source" add column api_item_id bigint;
alter table "record_source" add constraint fk_record_source_external_api_item foreign key (api_item_id)
    references "external_api_item" (id) on delete cascade;
alter table "record_source" add constraint ck_record_source_origin check ((email_id is null) <> (api_item_id is null));
alter table "record_source" add constraint uq_record_source_record_item_role unique (collection_id, record_id, api_item_id, role);
create index ix_record_source_api_item_id on "record_source" (api_item_id);
comment on column "record_source".api_item_id is 'The external API item the record was created or updated from; set instead of email_id';

alter table "proposal" add column source_api_item_id bigint;
alter table "proposal" add constraint fk_proposal_external_api_item foreign key (source_api_item_id)
    references "external_api_item" (id) on delete set null;
create index ix_proposal_source_api_item_id on "proposal" (source_api_item_id);
alter table "proposal" drop constraint ck_proposal_kind;
alter table "proposal" add constraint ck_proposal_kind check (kind in ('CREATE_RECORD', 'UPDATE_RECORD', 'CREATE_RELATION',
    'SET_LABEL', 'ARCHIVE_EMAIL', 'DRAFT_REPLY', 'CREATE_COLLECTION', 'ADD_FIELD', 'CREATE_RULE', 'CREATE_LABEL', 'INSIGHT',
    'MAP_API_ENDPOINT'));

alter table "ai_settings" add column external_apis text not null default 'SUGGEST';
alter table "ai_settings" add constraint ck_ai_settings_external_apis check (external_apis in ('OFF', 'SUGGEST', 'AUTO'));
