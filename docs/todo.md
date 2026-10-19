# To-do

Open work that was discussed and deliberately postponed. Newest first; remove an item when it ships.

## Reports sidebar on the record page ("reports this record is in")

**Goal:** the record page's Reports tab already has a closable side list (search, New report) of the reports
started on the record. It should also list every report the record appears in.

**Why it waits:** the API cannot answer it yet.
- `GET /api/collections/{c}/records/{r}/reports` lists only the reports started on this record.
- `GET /api/collections/{c}/reports` lists whole-collection reports, but the summaries do not say which records a
  report covers.
- Nothing lists reports of other records whose sources cite this record.

**Plan (tests first, per CLAUDE.md):**
1. Spec: `GET /api/collections/{collectionId}/records/{recordId}/reports/mentions` → `ReportSummaryDto[]` (plus where
   the report lives: `collectionId`, `recordId` of its subject, so the UI can link it), newest first. Reports whose
   `records` or citations (`report_citation`) include `(collectionId, recordId)`, excluding the record's own reports.
2. Backend: repository query over the report records / citations (scoped to the current user), service, controller,
   one test per layer.
3. Frontend: a second group "Mentioned in" in the existing side list (`RecordReports`), each opening its report
   embedded or full screen. Tests for the group and its empty state.
4. README + design language (sidebar pattern).
