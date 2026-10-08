-- The email an analysis run is reading right now (shown as "Now reading: <subject>"); cleared when the run ends.
alter table "ai_analysis_run" add column current_email_id bigint;
alter table "ai_analysis_run" add constraint fk_ai_analysis_run_email_message
    foreign key (current_email_id) references "email_message" (id) on delete set null;
create index ix_ai_analysis_run_current_email_id on "ai_analysis_run" (current_email_id);
