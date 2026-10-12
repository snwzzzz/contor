-- Insights: proposals about patterns across several emails (an unanswered question, overlapping appointments, what the
-- model notices in the last two weeks). Approving one creates its follow-up to-dos and appointments.
alter table "proposal" drop constraint ck_proposal_kind;
alter table "proposal" add constraint ck_proposal_kind check (kind in ('CREATE_RECORD', 'UPDATE_RECORD', 'CREATE_RELATION',
    'SET_LABEL', 'ARCHIVE_EMAIL', 'DRAFT_REPLY', 'CREATE_COLLECTION', 'ADD_FIELD', 'CREATE_RULE', 'CREATE_LABEL', 'INSIGHT'));

-- An insight is proposed once: its key (what it is about) is looked up before a new one is stored, whatever the status
-- of the earlier one (pending, approved or dismissed).
create index ix_proposal_insight_key on "proposal" ((payload ->> 'insightKey')) where kind = 'INSIGHT';
