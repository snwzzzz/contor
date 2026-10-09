-- Sent items are synced too (folder 'sent'), as context for replies and "already answered" checks.
-- They are never processed by the AI (processing_state SKIPPED) and never listed in the inbox.
-- A second Graph delta link tracks the sent folder independently of the inbox.
alter table "mail_account" add column sent_delta_link text;
comment on column "mail_account".sent_delta_link is 'Graph delta link of the Sent Items folder (inbox: delta_link)';
