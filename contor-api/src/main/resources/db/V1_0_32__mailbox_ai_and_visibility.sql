-- Per mailbox: whether the AI reads it (analysis and new mail) and whether its mail is shown in the app.
alter table "mail_account"
    add column ai_enabled boolean not null default true,
    add column hidden     boolean not null default false;

comment on column "mail_account".ai_enabled is 'The AI reads this mailbox; off = its mail is never given to the model';
comment on column "mail_account".hidden is 'Its mail is not shown in the app; whether the AI still reads it is ai_settings.read_hidden_mailboxes';

-- Per user: whether the AI also reads mailboxes hidden in the app (default yes).
alter table "ai_settings"
    add column read_hidden_mailboxes boolean not null default true;

comment on column "ai_settings".read_hidden_mailboxes is 'The AI also reads hidden mailboxes (as long as their ai_enabled is on)';
