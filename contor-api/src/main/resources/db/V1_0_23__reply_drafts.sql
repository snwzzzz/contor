-- A reply draft the user kept (an approved DRAFT_REPLY proposal, possibly edited). Never sent and never written to the
-- mailbox: the user copies it or opens it in the mail app. Later: IN_OUTLOOK (written to Outlook's Drafts), SENT.
create sequence reply_draft_seq start with 1 increment by 50;

create table "reply_draft" (
    id bigint not null,
    email_id bigint not null,
    account_id bigint not null,
    conversation_id text,
    to_recipients jsonb not null default '[]'::jsonb,
    cc_recipients jsonb not null default '[]'::jsonb,
    subject text not null,
    body text not null,
    status text not null default 'READY',
    proposal_id bigint,
    created_at timestamptz not null,
    last_modified timestamptz,
    constraint pk_reply_draft primary key (id),
    constraint fk_reply_draft_email_message foreign key (email_id) references "email_message" (id) on delete cascade,
    constraint fk_reply_draft_mail_account foreign key (account_id) references "mail_account" (id) on delete cascade,
    constraint fk_reply_draft_proposal foreign key (proposal_id) references "proposal" (id) on delete set null,
    constraint ck_reply_draft_status check (status in ('READY', 'DISCARDED'))
);
create index ix_reply_draft_email_id on "reply_draft" (email_id);
create index ix_reply_draft_account_conversation on "reply_draft" (account_id, conversation_id);
create index ix_reply_draft_proposal_id on "reply_draft" (proposal_id);
comment on table "reply_draft" is 'Reply drafts the user kept from DRAFT_REPLY proposals; never sent (copy / mailto only)';
