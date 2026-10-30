-- Chat answers can cite an item of one of the user's apps (external APIs): the citation keeps the item's data as cited
-- (quote), and the link to the item goes when the app is deleted.
alter table "chat_citation" add column api_item_id bigint;
alter table "chat_citation" add constraint fk_chat_citation_external_api_item foreign key (api_item_id)
    references "external_api_item" (id) on delete set null;
create index ix_chat_citation_api_item_id on "chat_citation" (api_item_id);
alter table "chat_citation" drop constraint ck_chat_citation_type;
alter table "chat_citation" add constraint ck_chat_citation_type check (type in ('RECORD', 'FIELD', 'EMAIL_PARAGRAPH', 'RELATION', 'APP_ITEM'));
alter table "chat_citation" drop constraint ck_chat_citation_target;
alter table "chat_citation" add constraint ck_chat_citation_target check (
    (type = 'RECORD' and collection_id is not null and record_id is not null)
    or (type = 'FIELD' and collection_id is not null and record_id is not null and field_slug is not null)
    or (type = 'EMAIL_PARAGRAPH' and paragraph_index is not null and paragraph_hash is not null
        and segmenter_version is not null and quote is not null)
    or (type = 'RELATION' and relation_id is not null)
    or (type = 'APP_ITEM' and quote is not null));
