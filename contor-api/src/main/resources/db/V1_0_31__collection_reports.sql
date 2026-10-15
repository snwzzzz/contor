-- Reports over a whole collection: a report without a record (record_id null) covers the records of its collection
-- that the filters and search in its scope (scope.collection) select. Everything else is shared with record reports.
alter table "report" alter column record_id drop not null;

-- The collection's own reports, newest first (record reports use ix_report_record).
create index ix_report_collection on "report" (collection_id, created_at desc) where record_id is null;

comment on column "report".record_id is 'The anchor record; null for a report over the whole collection (scope.collection holds its filters)';
