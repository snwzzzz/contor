-- Progress of the category discovery (one step per model call: email batches plus the merge), shown while it runs.
alter table "ai_analysis_run" add column discovery_steps int not null default 0;
alter table "ai_analysis_run" add column discovery_steps_done int not null default 0;
alter table "ai_analysis_run" add constraint ck_ai_analysis_run_discovery_steps
    check (discovery_steps >= 0 and discovery_steps_done between 0 and discovery_steps);
