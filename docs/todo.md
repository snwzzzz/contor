# To-do

Open work that was discussed and deliberately postponed. Newest first; remove an item when it ships.

## Language setting (Settings → General)

**Goal:** pick the interface language (e.g. English, German) in Settings → General.

**Today:** the row is there but shows English only, disabled ("More languages will follow."; `GeneralSettings`).

**Plan (tests first, per CLAUDE.md):**
1. Decide the i18n approach for Next.js 16 (read `node_modules/next/dist/docs/` on internationalization first) and where
   the choice lives: per user on the server (spec change: a `locale` on the user/preferences, asked before changing the
   spec) or per browser.
2. Move the UI strings into message files; English as the source, German as the first translation.
3. Enable the select, apply the language without a reload, format dates and numbers by locale.
4. README + design language.

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
