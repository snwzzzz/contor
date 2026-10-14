-- Who the user is in their own words (role, company, customers and suppliers). The extraction prompt uses it to know
-- whose side an email is on, e.g. that a customer's "PO" is an order the user's company receives.
alter table "ai_settings" add column owner_profile text;
comment on column "ai_settings".owner_profile is 'Free text about the user and their business for the AI prompts; null = unknown';
