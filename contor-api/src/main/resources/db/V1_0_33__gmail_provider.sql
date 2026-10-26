-- Gmail as a second synced mail provider. The sync cursors are provider-neutral now: a Graph delta link or a Gmail
-- history id, whatever the provider's MailboxClient hands back.
alter table "mail_account" drop constraint ck_mail_account_provider;
alter table "mail_account" add constraint ck_mail_account_provider check (provider in ('MICROSOFT_GRAPH', 'GMAIL', 'DEMO'));

alter table "mail_account" rename column delta_link to sync_cursor;
alter table "mail_account" rename column sent_delta_link to sent_sync_cursor;
comment on column "mail_account".sync_cursor is 'Provider sync cursor of the inbox (Graph delta link, Gmail history id)';
comment on column "mail_account".sent_sync_cursor is 'Provider sync cursor of the sent mail (Graph delta link, Gmail history id)';
