-- The dev-only demo mailbox (fictional business dataset, ai-eval/business) is stored under its own provider so the
-- mail sync never tries to reach Microsoft for it.
alter table "mail_account" drop constraint ck_mail_account_provider;
alter table "mail_account" add constraint ck_mail_account_provider check (provider in ('MICROSOFT_GRAPH', 'DEMO'));
