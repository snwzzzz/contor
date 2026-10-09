-- What applying a proposal produced (e.g. {"collectionId": 1, "recordId": 9}); later proposals and undo read it.
alter table "proposal" add column result jsonb;
comment on column "proposal".result is 'What applying the proposal produced, e.g. the created record; also the base for undo data';

-- A proposal that is AUTO but waits for proposals it depends on is applied by the system as soon as they are applied.
alter table "proposal" add column auto_apply_when_ready boolean not null default false;
comment on column "proposal".auto_apply_when_ready is 'AUTO proposal waiting for its dependencies: applied automatically once they are applied';

-- A proposal may only be decided once the proposals it depends on are applied (e.g. a relation needs both records).
-- Rejecting a proposal rejects its pending dependents.
create table "proposal_dependency" (
    proposal_id bigint not null,
    depends_on_proposal_id bigint not null,
    constraint pk_proposal_dependency primary key (proposal_id, depends_on_proposal_id),
    constraint fk_proposal_dependency_proposal foreign key (proposal_id) references "proposal" (id) on delete cascade,
    constraint fk_proposal_dependency_depends_on foreign key (depends_on_proposal_id) references "proposal" (id) on delete cascade,
    constraint ck_proposal_dependency_not_self check (proposal_id <> depends_on_proposal_id)
);
create index ix_proposal_dependency_depends_on on "proposal_dependency" (depends_on_proposal_id);
comment on table "proposal_dependency" is 'Edges "proposal_id needs depends_on_proposal_id to be applied first"; acyclic (guarded in ProposalService)';
