-- Cached logo (icon) of a sender's mail domain, fetched by the backend from https://<domain>.
-- image is null when the domain has none (negative cache, retried after a week); positive entries refresh monthly.
create sequence sender_logo_seq start with 1 increment by 50;

create table "sender_logo" (
    id bigint not null,
    domain text not null,
    image bytea,
    content_type text,
    fetched_at timestamptz not null,
    created_at timestamptz not null,
    last_modified timestamptz,
    constraint pk_sender_logo primary key (id),
    constraint uq_sender_logo_domain unique (domain)
);

-- Photos of the mailbox owner's Outlook contacts, keyed by contact address; replaced per account at most daily.
create sequence contact_photo_seq start with 1 increment by 50;

create table "contact_photo" (
    id bigint not null,
    account_id bigint not null,
    email_address text not null,
    image bytea not null,
    content_type text not null,
    created_at timestamptz not null,
    last_modified timestamptz,
    constraint pk_contact_photo primary key (id),
    constraint fk_contact_photo_mail_account foreign key (account_id) references "mail_account" (id) on delete cascade,
    constraint uq_contact_photo_account_email unique (account_id, email_address)
);
create index ix_contact_photo_account_id on "contact_photo" (account_id);

-- When contact photos were last read from Microsoft Graph for a mailbox.
alter table "mail_account" add column contacts_checked_at timestamptz;
