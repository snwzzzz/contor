-- Courtesy replies: with reply drafts on, the assistant may also suggest a short polite reply to emails that need no
-- answer but usually get one from a person (a job rejection, an offer you decline, a thank-you). Off by default.
alter table "ai_settings" add column courtesy_replies boolean not null default false;
comment on column "ai_settings".courtesy_replies is 'Also suggest optional polite replies (rejections, thank-yous); needs replies = SUGGEST';
