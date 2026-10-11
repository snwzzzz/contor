-- Reply drafts: the assistant may write a draft reply for inbox emails that need an answer (OFF or SUGGEST).
-- There is no AUTO: a draft is never sent, so applying it automatically would do nothing.
alter table "ai_settings" add column replies text not null default 'OFF';
alter table "ai_settings" add constraint ck_ai_settings_replies check (replies in ('OFF', 'SUGGEST'));
comment on column "ai_settings".replies is 'Reply drafts for emails that need an answer: OFF or SUGGEST (never sent)';
