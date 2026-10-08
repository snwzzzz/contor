-- Profile photo of the connected mailbox (Microsoft Graph /me/photo), shown next to the account in the UI.
-- photo_checked_at: when contor last asked Graph for it, so accounts without a photo are not re-fetched on every sync.
alter table "mail_account"
    add column photo bytea,
    add column photo_content_type text,
    add column photo_checked_at timestamptz;
