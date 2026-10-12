-- How much a proposal deserves the user's attention (ImportanceScorer): a 0-100 score for sorting, the level derived from
-- it by fixed thresholds (LOW < 35 <= NORMAL < 60 <= HIGH < 80 <= URGENT) for filtering, and the reason the UI shows.
-- Existing proposals start as NORMAL/50 without a reason; pending ones are scored once at startup.
alter table "proposal"
    add column importance text not null default 'NORMAL',
    add column importance_score integer not null default 50,
    add column importance_reason text,
    add constraint ck_proposal_importance check (importance in ('LOW', 'NORMAL', 'HIGH', 'URGENT')),
    add constraint ck_proposal_importance_score check (importance_score between 0 and 100);

create index ix_proposal_status_importance on "proposal" (status, importance_score desc, created_at desc);

comment on column "proposal".importance is 'LOW, NORMAL, HIGH or URGENT, derived from importance_score';
comment on column "proposal".importance_score is '0-100, higher first when sorting by importance';
comment on column "proposal".importance_reason is 'Why the proposal got its importance, e.g. "a to-do; due within 2 days"';
