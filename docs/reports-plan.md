# AI reports implementation plan (any record, its connections, fact-checked, cited to the paragraph)

> Living plan. Each step is done test-first (CLAUDE.md §0), goes through the spec first (§0.3) and updates the README
> in the same change. **Status:** first slice implemented — R1–R6, R8 and R10 (tables, paragraphs, evidence pack,
> fact-checked generation, API, Details-tab prompt, report page with sources panel). Open: R7 (eval), R9 (Connections-tab
> scope picker), R11 (deep links that highlight the cited field/paragraph on its own page), R12. Branch:
> `feature/report-section`.
>
> Implementation notes (where it differs from or refines the text below):
> - R1: the migration is `V1_0_29` (1.0.27 and 1.0.28 came with the business workflows on `developer`). Only `report` has a sequence; the four child tables use identity IDs (detail tables, CLAUDE.md §2).
>   `ck_report_citation_target` does not require `email_id` for EMAIL_PARAGRAPH (a deleted email sets it null; the
>   quote, hash and index stay). Sentences of a finding live in `ReportFinding.claims`; `ReportSection.claims` holds
>   only the sentences outside findings (`@SQLRestriction("finding_id is null")`).
> - R2: `ReportScope` (domain value record) validates records (1–25, no duplicates), depth (0–2) and emails (≤ 60).
>   `ReportBusyException` (409), `ReportNotFoundException` (404); audit actions `REPORT_GENERATED`, `REPORT_DELETED`.
> - R4: `ai/EvidencePack` ranks emails by prompt words (no embedding ranking yet); paragraph tokens are 1-based
>   (`P1.1` = first paragraph, `paragraphIndex` 0). Sender addresses are reduced to their domain in the prompt.
> - R5: `ReportModelService` (draft `report-v1`, verify, repair) and `ReportGenerator`. The model may not set CONFLICT;
>   only the verifier does. Timeline entries are deterministic (no model captions) and not counted as sentences. Single
>   digits are exempt from the number check (counts); a day difference between two cited dates is allowed.
> - R6: "AI not ready" is 409 (`AiUnavailableException`, the codebase convention), not 503. A question about a follow-up
>   goes to its report. `ReportDto.sources` is one entry per marker; markers are numbered per report (follow-ups have
>   their own). `/cited-in` is not built yet (R9).
> - Live run (qwen3.5:9b, demo PO-26-1083, records only): ready in 18–52 s. The model cites fields as `R1:F3`, so
>   `EvidencePack.normalizeToken` takes the most specific token in a reference, and the prompt says to write tokens
>   alone. A sentence that cited nothing is offered the record facts in the repair (refs returned, at most 3 kept); a
>   sentence that repeats an earlier one is removed. Before these fixes 10 of 12 sentences were removed, after them 0 of 6.
> - Live run with emails (demo PO-26-1083, 10 emails, ~2 min): the same draft went from 10 of 18 sentences verified to 15
>   of 18 after three changes: dates without a year ("29.07.", "24 July") in a source take the year of its email; a
>   finding's sentence may rely on the paragraph its finding quotes; a rewrite that changes nothing after the verifier
>   rejected the sentence is removed. The verifier must list every fact of a SUPPORTED sentence with a verbatim quote
>   from one of the sentence's own sources (checked in code), else the verdict becomes UNSUPPORTED. Known limit of the 9B
>   model: a wrong conclusion built from correctly quoted parts ("by 2026-07-24, as agreed" while the rest arrived on
>   29.07.) still passes, and a paraphrased verifier quote removes a correct sentence now and then. R7 should measure both.
> - Live run on the user's case (complaint BA-2026-07, 8 German emails, "summarize … what went wrong and how we can do
>   better"): recommendations were all removed (advice is never stated in a source), the repair turned them into "the
>   source mentions…" sentences, and the follow-up repeated the report or came back empty. Now (`report-v2`): next steps
>   are "Recommendations and next steps" and, like open questions, skip the verifier (deterministic checks only) and are
>   marked as suggestions in the UI; sentences about the sources are rejected; inline sources ("(R1, F1-F3)",
>   "(E3:P3.1)") and whole emails (`E3`) are understood; the verifier sees each email's date and its quotes may span the
>   sentence's sources with the prompt's separators; follow-ups always answer their question; an empty draft is retried
>   once. Same data: report 13 of 16 verified with 4 recommendations (was 0 of 16), follow-up 10 of 12 (was empty).
> - **Fact check replaced by references (user decision, 2026-10-15):** the verifier, the repair pass and the deterministic
>   number/date checks are gone (`ClaimChecks` removed). The user wants answers to their questions with a reference where
>   there is one, not removed sentences. `ai/References` resolves codes (in refs, in brackets, or written as words) and
>   keeps only verbatim quotes. The model writes a free-text `answer` first (`report-v4`; the 9B model left the
>   structured summary list empty and copied email sentences instead); follow-ups drop sentences the report already
>   has and get no timeline. Related emails (embeddings) and the records linked to them are part of every report, for
>   "look at other orders" questions. Live, on complaint "Wrong item SO-26-0155": the first answer has a direct answer
>   with recommendations, "what do you think went wrong?" gets an opinion, and "look at other emails and orders" finds
>   other orders and an unrelated misloading case.
> - R8/R10: `components/app/reports/` (`RecordReports`, `ReportPrompt`, `ReportView`, `ReportBody`, `CitationMarker`,
>   `SourcePanel`, `FactCheck`), `lib/report.ts`. The sources panel shows the cited email paragraph with its neighbours
>   (`GET /api/emails/{id}/paragraphs`) and the quote marked; source links already carry `?tab=…&field=…` /
>   `&paragraph=…` for R11.
> - R3: `ai/HtmlText` (jsoup) treats `p`/headings/lists/tables as paragraphs and `div`/`br`/rows as lines; when a mail
>   has empty spacer paragraphs (Outlook), `p` is a line and the spacers make the breaks. `EmailParagraphs` hashes are
>   16 hex chars of SHA-256 over the normalized text; `normalize` (case, whitespace, typographic quotes and dashes) is
>   what the fact check will use to compare quotes. The endpoint lives in `MailService`/`MailController` (Mail tag).
>
> Revision 2: AI-first and collection-agnostic (replaces the rules/field-roles design of revision 1).

## Goal

For **any record in any collection**, ask the local model for a report in plain words — "what went wrong with this
order?", "summarize everything about this supplier", "where do we stand with this project?" — and get a research-style
report in the manner of Listen Labs' study reports: an executive summary first, then key findings, each claim backed by
**verbatim quotes and data points you can click to the exact source**.

1. **Small chat prompt in the Details tab.** One input ("Ask about this record or describe the report you want…") plus a
   few suggestion chips (Summary, What went wrong, Timeline, Open questions). Follow-up questions continue the same
   conversation.
2. **Connections tab as the scope.** "Report on connections" lets you tick which connected records (depth 1–2) go into
   the report: an order with its deliveries, invoices, supplier and their emails.
3. **Fact-checked.** Every sentence goes through deterministic checks (do the cited sources exist, is the quote
   verbatim, do the numbers and dates appear in the sources) and a separate model verification pass. Unsupported claims
   are removed or rewritten; contradictions between sources become their own finding. The report shows what was checked.
4. **Cited to the paragraph.** Claims link to a **paragraph of an email** (quote highlighted in the email), to a **field
   of a record** (field highlighted), or to a **connection**. Hovering a citation shows the quote; clicking opens a
   source panel next to the report, and from there the place in contor.

Nothing in this plan assumes a specific collection: the model gets the record's fields as they are defined.

## The report (target layout)

```
┌ Report: Order 4711 and its connections ──────────────── Oct 14 · 5 records · 9 emails · 18/19 claims verified ┐
│ Prompt: "What went wrong with this order and what is still open?"                                               │
│                                                                                                                 │
│ Executive summary                                                                    │ Sources                  │
│ The order was delivered five days late [1][2] after the carrier reported a           │ [1] orders/4711 ·        │
│ customs hold [3]. The invoice is € 10 above the order total [4][5] …                 │     expected_delivery    │
│                                                                                      │     2026-09-02           │
│ Key findings                                                                         │ [3] Email "Delay notice" │
│ ▸ Delivery was late because of a customs hold                     PROBLEM · 3 sources│     DHL · Sep 4 · ¶2     │
│   "Your shipment is held at customs pending documents" — DHL, Sep 4 [3]              │     "Your shipment is    │
│ ▸ Supplier reacted quickly                                        POSITIVE · 2       │     held at customs…"    │
│ ▸ Conflicting amounts: order € 110 vs invoice € 120               CONFLICT · 2       │     ▸ Open in inbox      │
│                                                                                      │                          │
│ Timeline · Open questions · Next steps · (sections the prompt asked for)             │                          │
│                                                                                      │                          │
│ Fact check: 19 claims, 18 verified, 1 removed (no source said "two weeks")  ▸ details│                          │
│ ─────────────────────────────────────────────────────────────────────────────────────                          │
│ [ Ask a follow-up…                                                         ] [Send]                             │
└─────────────────────────────────────────────────────────────────────────────────────────────────────────────────┘
```

Sections: **Executive summary** (always), **Key findings** (always; each a title, a severity — POSITIVE, NEUTRAL,
PROBLEM, RISK, CONFLICT — 1–4 claim sentences and 1–3 supporting quotes), **Timeline** (always, built from dates in
fields, email dates and connection dates), **Open questions**, **Next steps**, plus **custom sections** the prompt asks
for ("Delivery problems", "Payment history"). Sections the evidence cannot fill are left out instead of padded.

## What exists already (and is reused)

| Piece | Where | Use |
|---|---|---|
| Records of any collection, typed values | `RecordService.getRecord`, `DynamicRecordRepository`, field handlers in `fieldtype/` | the record's facts, formatted per field type |
| Connections | `relation`/`relation_type` (`V1_0_8`), `RelationService.listRelations(collectionId, recordId)` | scope expansion (depth 1–2) |
| Record ↔ email | `record_source` (`V1_0_7`), `RecordSourceRepository`, `RecordService` emails endpoint | which emails belong to the scope |
| Threads | `email_message.conversation_id` + thread query (`/api/emails/{emailId}/thread`) | include the replies of linked emails |
| Email text | `email_message.body_text` / `body_html`, `ai/EmailTextCleaner` (strips quoted history, signatures) | paragraph segmentation |
| Email embeddings | `EmailEmbeddingService`, `EmbeddingClient`, pgvector (`ADR 0008`) | pick the most relevant emails when the scope is too big for the context |
| Structured model output | `LlmClient.completeJson`, `JsonSchemas`, `CachingLlmClient` (dev cache) | draft, verify, repair passes |
| Validated model citations | `InsightModelService` (evidence ids checked against the digest) | the same "untrusted output" pattern, here per sentence |
| AI thread + status | `AiExecutors`, `AiModelService` / `GET /api/ai/status` | background generation, "model unavailable" |
| Record page tabs | `components/app/collections/RecordDetail.tsx` (`DetailsTab`), `RecordConnectionsTab.tsx`, `RecordEmailsTab.tsx` | chat prompt, scope picker, report list |
| Email deep link | `/inbox?email=<id>` (`InboxView.tsx`), `EmailReader.tsx`, `EmailHtmlBody.tsx` (sandboxed, same-origin frame) | paragraph highlight |
| Proposal evidence links | `components/app/proposals/InsightCard.tsx` | link style for email sources |

## Decisions (proposed defaults — confirm or change)

1. **A report belongs to one anchor record** (`collection_id, record_id`) and a scope (the anchor plus chosen
   connected records). The chat on that record is a list of reports and follow-up answers; each answer is a stored,
   cited, fact-checked "mini report" (same structure, fewer sections).
2. **Snapshots.** Quotes, field values and paragraph texts are stored with the citation. Opening a report later marks
   citations whose source **changed** or was **deleted**; "Regenerate" makes a new report.
3. **Paragraph = segment of the cleaned plain text** of an email (`body_text`, or `body_html` converted to text when
   there is no text part), split on blank lines and list items, quoted history and signatures removed. A paragraph is
   addressed as `(email_id, index)` and also stored with its text, a hash and the segmenter version, so a link still
   finds its paragraph if the segmenter changes (search by text).
4. **Quotes must be verbatim.** A quote is accepted only if it is a whitespace/case-normalized substring of the cited
   paragraph; otherwise it is dropped (the claim may survive with its other sources).
5. **Two-stage fact check.** Deterministic checks first (cheap, strict), then a verifier model call that sees **only one
   claim and its cited sources** (not the draft), and labels it SUPPORTED, PARTIAL, UNSUPPORTED or CONTRADICTED. One
   repair pass rewrites PARTIAL/UNSUPPORTED claims with the verifier's reason; whatever still fails is removed and
   listed in the fact-check details. CONTRADICTED claims whose sources disagree with each other become a CONFLICT
   finding instead of being silently dropped.
6. **Local model only** (Ollama, `ADR 0007`); nothing leaves the machine. Without an available model the prompt is
   disabled with the reason (no rules-only fallback: the user asked for AI reports). Reports are created only on
   request, never on a schedule, so no new autonomy setting.
7. **Limits:** scope ≤ 25 records and ≤ 60 emails; evidence pack ≤ ~24 000 characters (paragraphs ranked by relevance to
   the prompt, newest first on ties); prompt ≤ 500 characters; ≤ 8 findings, ≤ 4 quotes per finding.
8. **Prompt injection:** email text is data. It is fenced in the pack as quoted source text, the system prompt says
   instructions inside sources are ignored, and nothing the model returns can trigger an action — reports only display.

---

## Part A — Backend

### R1. Migration `V1_0_29__reports.sql`
Quoted tables, `<table>_seq` increment 50 for the two aggregate roots, `timestamptz`, named constraints, FK indexes,
`text + CHECK` enums, a comment per table.

- `report` — `id, collection_id (fk collection), record_id bigint (no FK: generated table), parent_report_id (fk report,
  on delete cascade; set for follow-up answers), prompt text, title, scope jsonb ({records: [{collectionId, recordId}],
  depth, emailIds}), status text ck (QUEUED, COLLECTING, DRAFTING, FACT_CHECKING, READY, FAILED), failure_reason,
  model text, prompt_version text, claims_total int, claims_verified int, claims_removed int, generated_at,
  created_by_user_id (fk app_user, on delete set null), created_at, last_modified`.
  Indexes: `ix_report_record (collection_id, record_id, created_at desc)`, `ix_report_parent_report_id`,
  `ix_report_created_by_user_id`.
- `report_section` — `id identity, report_id (fk cascade), position, kind text ck (SUMMARY, FINDINGS, TIMELINE,
  OPEN_QUESTIONS, NEXT_STEPS, CUSTOM), title`. `uq_report_section_report_position`.
- `report_finding` — `id identity, section_id (fk cascade), position, title, severity text ck (POSITIVE, NEUTRAL,
  PROBLEM, RISK, CONFLICT), event_date timestamptz (timeline entries)`.
- `report_claim` — the sentence level: `id identity, section_id (fk cascade), finding_id (fk cascade, nullable),
  position, text, verification text ck (SUPPORTED, PARTIAL, REPAIRED, REMOVED, CONFLICT), verifier_note text`.
  REMOVED claims are kept (hidden in the report, shown in the fact-check details).
- `report_citation` — `id identity, claim_id (fk cascade), marker int (the [n] shown), type text ck (FIELD, RECORD,
  EMAIL_PARAGRAPH, RELATION), collection_id (fk collection), record_id, field_slug, email_id (fk email_message, on
  delete set null), paragraph_index int, paragraph_hash text, segmenter_version int, quote text, relation_id bigint,
  value_snapshot jsonb, is_supporting_quote boolean`. `ck_report_citation_target` (required columns per type),
  `ix_report_citation_claim_id`, `ix_report_citation_email_id`, `ix_report_citation_record (collection_id, record_id)`
  (reverse lookup: "reports citing this record").

Tests: `@RepositoryTest` per repository: cascade delete (report → sections → claims → citations, follow-ups), the
target check (EMAIL_PARAGRAPH without `paragraph_index` rejected; one DB error per test).

### R2. Domain, persistence, exceptions, audit
- `domain/Report` (typed `@EmbeddedId`, `AuditableEntity`, `@OneToMany` sections, follow-ups via `parentReport`),
  child entities `ReportSection`, `ReportFinding`, `ReportClaim`, `ReportCitation`; enums `ReportStatus`,
  `ReportSectionKind`, `FindingSeverity`, `ClaimVerification`, `CitationType`; JSON record `ReportScope` (validating).
- `persistence/ReportRepository` (`findByCollectionIdAndRecordIdAndParentReportIsNullOrderByCreatedAtDesc`,
  `findByParentReport_Id`), `ReportCitationRepository.findReportIdsCiting(collectionId, recordId)`.
- `exception/ReportNotFoundException` (→ 404), `ReportBusyException extends ConflictException` (follow-up or
  regenerate while the report is still generating), scope/prompt violations as `IllegalArgumentException` (→ 400),
  `AiUnavailableException` (exists, → 503).
- `AuditAction.REPORT_GENERATED`, `REPORT_DELETED` (scope size and model only; never prompt or mail text).
- Tests: value object `ReportScope` (empty, too many records, duplicate refs), mapper tests in R6.

### R3. Paragraph segmentation (`ai/EmailParagraphs`)
- `static List<Paragraph> split(String bodyText, String bodyHtml)` → `Paragraph(index, text, hash)`;
  `SEGMENTER_VERSION = 1`. HTML without a text part is converted with jsoup in `ai/HtmlText`
  (block elements → line breaks, entities decoded, scripts/styles dropped).
- Uses `EmailTextCleaner` rules to drop quoted history, reply headers, signatures, mobile footers; merges one-line
  fragments ("Hi Tom,") into the next paragraph; caps a paragraph at 1 200 chars (split at sentence end).
- `static Optional<Integer> locate(List<Paragraph>, String quote)` — normalized substring search; used by the fact
  check and by the reader for old citations.
- API: `GET /api/emails/{emailId}/paragraphs` → `EmailParagraphDto[] {index, text, hash}` (mailbox-scoped like the
  email itself), so the frontend highlights exactly what the backend cited.
- Tests: plain unit tests with real-world fixtures (German/English replies, Outlook "From:/Sent:" blocks, lists, HTML
  only, empty body), `locate` with whitespace/case differences and a quote spanning a soft line break.

### R4. Scope and evidence pack (`service/ReportScopeService`, `ai/EvidencePack`)
1. **Scope:** the anchor record + the connected records the user ticked (validated: each must really be connected
   within the chosen depth via `RelationService`), ≤ 25.
2. **Facts per record:** title, status, every non-archived field with its value formatted by type (dates ISO, MONEY
   with currency, ENUM/STATUS values), created/modified, created by. Each line gets a ref token: `[F12]` =
   `orders/4711.expected_delivery = 2026-09-02`.
3. **Connections:** `[L5]` = `orders/4711 —delivered by→ deliveries/88 (since 2026-09-01)`.
4. **Emails:** via `record_source` for every scope record, plus their thread messages (sent replies included), only
   from the current user's mailboxes (`EmailSpecifications.Criteria.ownerId`), ≤ 60. Each paragraph is a token:
   `[P7.2]` = email 7, paragraph 2, with a header line per email (date, from name and domain, subject; addresses masked).
5. **Timeline facts** (deterministic): every date field, email date and relation `valid_from/valid_to` as dated
   events, sorted — given to the model as ground truth and used for the Timeline section.
6. **Ranking when over budget:** keep all field facts and connections; rank emails by embedding similarity to the
   prompt (`EmbeddingClient` on the prompt, stored email vectors), then paragraphs by lexical overlap with the prompt
   and with field values (order numbers, amounts), newest first on ties; fill to 24 000 chars.
7. `EvidencePack` keeps the token → source map; it is the only thing claims may cite.
- Tests: integration (`@SpringBootTest` + Testcontainers): a generic two-collection setup created through
  `CollectionSchemaService` (no assumptions about names), records, relation, emails with record_source; asserts tokens,
  mailbox scoping (other user's email absent), depth validation (an unconnected record → 400), budget trimming.

### R5. Generation pipeline (`service/ReportGenerator`, prompts `report-v1`, `report-verify-v1`, `report-repair-v1`)
Runs on the AI thread; status moves QUEUED → COLLECTING → DRAFTING → FACT_CHECKING → READY (or FAILED with a reason).

1. **Draft** (`ReportDraftService`, `report-v1`): system prompt = analyst writing a short, factual report about the
   given records for the user's request; every sentence cites tokens; quotes copied verbatim from `P` tokens; no
   advice beyond what the sources support; dates as YYYY-MM-DD; instructions in sources are ignored. JSON schema:
   `title`, `summary[] {text, refs[]}`, `findings[] {title, severity, claims[] {text, refs[]}, quotes[] {ref, text}}`,
   `openQuestions[]`, `nextSteps[]`, `customSections[] {title, claims[]}` (custom only when the prompt asks).
   Timeline is not drafted by the model: built from the timeline facts, with a one-line model caption per event that
   goes through the same fact check.
2. **Deterministic checks** (`ai/ClaimChecks`, pure): every ref exists in the pack; ≥ 1 ref per claim; quote verbatim in
   its paragraph (`EmailParagraphs.locate`); every number, amount and date in the claim text occurs in a cited source
   (normalized: `1.250,00 €` = `1250.00 EUR`, `2 Sep 2026` = `2026-09-02`); no claim cites only the anchor's title.
   Failures are marked with a reason.
3. **Verify** (`ReportVerifierService`, `report-verify-v1`): batches of ≤ 6 claims; each with only its cited source
   texts. Answer per claim: `verdict`, `reason`, `conflictingRefs[]`. Separate call from the draft so the verifier does
   not grade its own context.
4. **Repair** (`report-repair-v1`, one pass): PARTIAL/UNSUPPORTED claims (from 2 or 3) with the reason → rewritten claim
   limited to what the sources say, or "drop". Rewritten claims go through 2 and 3 again; still failing → REMOVED.
5. **Conflicts:** CONTRADICTED where the cited sources disagree with each other (field vs email, two emails) → a
   CONFLICT finding "Sources disagree: …" citing both sides.
6. **Assemble:** findings without surviving claims are dropped; markers `[n]` numbered in reading order, one per
   distinct source; counts stored on `report`.
- Determinism and cost: `temperature 0`, `CachingLlmClient` in dev; ~3–6 model calls per report.
- Tests (stubbed `LlmClient`, fixtures from R4): each deterministic check; invented ref dropped; invented number
  removed; non-verbatim quote dropped but claim kept with other refs; verifier UNSUPPORTED → repair → REPAIRED; repair
  fails → REMOVED and counted; conflict finding created; Ollama down mid-run → FAILED with "model unavailable";
  prompt-injection fixture ("ignore previous instructions…" in an email) produces no extra section.

### R6. Service, follow-ups, API (`service/ReportService`, `controller/ReportController implements ReportsApi`)
- `createReport(collectionId, recordId, NewReportCommand {prompt?, connectedRecords[], depth})` → stores QUEUED,
  queues the pipeline → 202. Empty prompt = default "Summarize this record: what happened, what went well, what went
  wrong, what is open".
- `askFollowUp(reportId, NewReportQuestionCommand {question})` → child report with the parent's scope and the parent's
  surviving claims as extra context; answer = summary + optional findings, same fact check → 202.
- `listReports(collectionId, recordId)` (top-level, newest first, with follow-up count), `getReport(reportId)`
  (sections → findings → claims → citations, follow-ups inline, `sourceState` CURRENT / CHANGED / DELETED computed by
  comparing snapshots: field values in one query per collection, paragraph hash for emails), `deleteReport`,
  `regenerate`, `listReportsCiting(collectionId, recordId)` (for connected records: "mentioned in 2 reports").
- Spec (`api-spec/openapi.yaml`, then regenerate):

| Method + path | Body / result |
|---|---|
| `GET /api/collections/{collectionId}/records/{recordId}/reports` | `ReportSummaryDto[]` |
| `POST /api/collections/{collectionId}/records/{recordId}/reports` | `NewReportCommand` → 202 `ReportDto` |
| `GET /api/reports/{reportId}` | `ReportDto` |
| `POST /api/reports/{reportId}/questions` | `NewReportQuestionCommand` → 202 `ReportDto` (the follow-up) |
| `POST /api/reports/{reportId}/regenerate` | 202 `ReportDto` |
| `DELETE /api/reports/{reportId}` | 204 |
| `GET /api/collections/{collectionId}/records/{recordId}/cited-in` | `ReportSummaryDto[]` |
| `GET /api/emails/{emailId}/paragraphs` | `EmailParagraphDto[]` (R3) |

  DTOs: `ReportDto {id, title, prompt, status, scope, sections[], followUps[], claimsTotal, claimsVerified,
  claimsRemoved, model, promptVersion, generatedAt}`, `ReportSectionDto`, `ReportFindingDto`, `ReportClaimDto
  {text, verification, verifierNote, citations[]}`, `ReportCitationDto {marker, type, collectionId, recordId, fieldSlug,
  fieldName, emailId, emailSubject, emailFrom, emailDate, paragraphIndex, quote, relationId, valueSnapshot,
  sourceState}`. API enums named apart from the domain ones and added to CLAUDE.md: `ReportState`↔`ReportStatus`,
  `ReportSectionType`↔`ReportSectionKind`, `FindingLevel`↔`FindingSeverity`, `ClaimCheck`↔`ClaimVerification`,
  `CitationKind`↔`CitationType`.
- Security: authenticated (default deny already covers it); a report's emails are filtered to the reader's mailboxes
  on read too, so a report created by the owner does not reveal another user's mail.
- Tests: service unit tests (`createReport_UnconnectedRecordRejected`, `createReport_AiUnavailable`,
  `askFollowUp_WhileGenerating_Conflict`, `getReport_MarksChangedField`, `getReport_HidesForeignMailboxCitations`),
  `ReportMapper` tests, `@ControllerTest` (202/204/400/404/409/503, 401, 403 without CSRF), integration flow with a
  stubbed `LlmClient` bean.

### R7. Evaluation (`-Pai-eval`)
- `ai-eval/reports/`: 6–8 synthetic cases on **different** collections (an order, a project, a person, a contract, an
  apartment search) with records, connections and emails, each with the facts a good report must contain and traps
  (a number only "almost" in the source, a contradiction, an injection line).
- `AiReportEvalTest`: citation precision (cited source really supports the claim, judged against labeled facts), fact
  recall, invented-number rate after the fact check, quote verbatim rate, removed-claim rate, latency per report.
  A prompt change bumps the version and is compared against two runs of the old prompt (CLAUDE.md §3).

---

## Part B — Frontend

Read `vercel-react-best-practices` and `contor-web/node_modules/next/dist/docs/` first. Composites in
`components/app/reports/`, pages only compose, data only through the generated `endpoints/reports/reports.ts` hooks.

### R8. Chat prompt in the Details tab
- `components/app/reports/RecordReportPrompt.tsx`, placed above the fields in `DetailsTab` (`RecordDetail.tsx`):
  a one-line auto-growing textarea (Enter sends, Shift+Enter new line, 500-char limit), suggestion chips (Summary, What
  went wrong?, Timeline, Open questions), a scope chip "This record + 3 connections ▾" (opens the picker from R9), and
  a send button. Disabled with the reason when `GET /api/ai/status` reports the model unavailable.
- Below it, `RecordReportThread.tsx`: the conversation for this record — each turn shows the prompt, a progress line
  while generating (Collecting sources → Writing → Fact-checking, via `refetchInterval` on `useGetReport` until READY
  or FAILED), then a compact card (title, summary's first sentences, verified count, "Open report"). Follow-ups typed in
  the prompt while a report is open go to `POST /api/reports/{id}/questions`.
- A **Reports** count in the tab bar is not added: the thread lives in Details as asked.
- Tests: send builds the right command; chip fills the prompt; disabled state; progress → card; follow-up targets the
  open report.

### R9. Connections tab as the report scope
- `RecordConnectionsTab.tsx`: a **Report on connections** button and a selection mode — a checkbox per connection
  (anchor always included), "include their connections" toggle (depth 2, lazily loads second-level relations with
  `useListRelations` per selected record), a counter "5 records · up to 25". "Generate" opens the prompt prefilled
  with the scope, or generates the default summary directly.
- Each connection row shows "cited in n reports" (`cited-in`) linking to them.
- The same picker component (`ReportScopePicker.tsx`) is used by the scope chip in R8.
- Tests: selection limits, depth toggle, the command's `connectedRecords`.

### R10. Report view (Listen Labs-style) and source panel
- Route `src/app/(app)/collections/[collectionId]/records/[recordId]/reports/[reportId]/page.tsx` (keeps the record
  context and breadcrumb) composing:
  - `ReportView.tsx` — header (title, prompt, scope chips linking to each record, date, model, "18/19 claims
    verified"), Executive summary, Key findings as cards (severity badge from tokens, claims with superscript markers,
    supporting quotes as blockquotes with sender, date and marker), Timeline (vertical list, dated, each entry cited),
    Open questions, Next steps, custom sections, follow-up answers in order, and the follow-up prompt at the bottom.
  - `CitationMarker.tsx` — `[n]` button; hover/focus shows a tooltip with the quote or field value; click selects the
    source in the panel. Marker colors by source state (CHANGED/DELETED badge), never hard-coded colors.
  - `SourcePanel.tsx` — right column (sheet on small screens): the list of all sources numbered like the markers;
    the selected one expanded: for EMAIL_PARAGRAPH the email header and its paragraphs (from
    `GET /api/emails/{id}/paragraphs`) with the cited one highlighted and scrolled into view, the quote marked inside
    it; for FIELD the record title with the field name, the snapshot value and the current value if changed; for
    RELATION both records and the relation type. Each with "Open in inbox" / "Open record".
  - `FactCheckDetails.tsx` — collapsible: per claim its verdict, verifier note, REPAIRED original vs final text, and
    REMOVED claims with the reason.
  - Actions: Regenerate, Delete, Copy as Markdown (with numbered source list).
- Tests: every citation type renders the right panel and links; markers numbered in order; changed/deleted states;
  removed claims hidden in the body but listed in the fact check; keyboard access to markers.

### R11. Deep links into the program (exact place, highlighted)
One helper `lib/report-links.ts` (+ unit tests) builds every link; all carry `report=<id>` for a "Back to report" chip.

| Citation | URL | On arrival |
|---|---|---|
| FIELD | `/collections/{c}/records/{r}?tab=details&field={slug}&report={id}` | Details tab, field scrolled into view and ringed for 2 s |
| RECORD | `/collections/{c}/records/{r}?report={id}` | record page |
| RELATION | `/collections/{c}/records/{r}?tab=connections&relation={id}&report={id}` | Connections tab, row highlighted |
| EMAIL_PARAGRAPH | `/inbox?email={id}&paragraph={index}&quote={short hash}&report={id}` | reading pane opens the email, the paragraph is highlighted and scrolled to |

- `RecordDetail.tsx`: initial tab from `useSearchParams().get("tab")`, `id="field-<slug>"` on the field items,
  highlight on `field`; `RecordConnectionsTab` highlights `relation`; "Back to report" chip when `report` is present.
- Email highlight: plain-text bodies render paragraphs as `<p data-paragraph="n">`; HTML bodies — `EmailHtmlBody.tsx`
  (same-origin sandboxed frame, no scripts) gets an optional `highlight` prop: after load, walk the frame's text nodes,
  find the cited paragraph's text (normalized) and wrap it in a `<mark>` styled in `FRAME_STYLE`, then scroll it into
  view. Not found (sender HTML differs from the text part) → fall back to showing the paragraph text above the body
  with "Quoted in report". Mark the edit in the component with a comment; it is an app composite, not `components/ui`.
- Tests: `RecordDetail.test.tsx` (tab/field from URL), `EmailHtmlBody.test.tsx` (mark inserted, fallback),
  `EmailReader.test.tsx` (plain-text paragraph highlight).

### R12. End-to-end, docs
- Playwright `e2e/reports.spec.ts` against the real backend with a stub model (a dev profile `LlmClient` returning
  fixture JSON, like the dev AI cache): create two collections, connect records, link an email → Details prompt
  "What went wrong?" → report READY → click marker → source panel shows the highlighted paragraph → "Open in inbox"
  highlights it → "Back to report"; Connections → select connections → report includes them.
- README: Reports section (how to ask, scope via Connections, fact check, limits, the eval command, model needs).
- `docs/architecture.md`: the pipeline; ADR `docs/decisions/0010-fact-checked-ai-reports.md` (snapshots, paragraph
  citations, two-stage fact check, no rules-only fallback). CLAUDE.md: enum pairs, "report claims are only stored with
  verified citations".

---

## Order of work

| Step | Depends on | Size |
|---|---|---|
| R1 migration, R2 domain | – | M |
| R3 paragraphs (+ endpoint) | – (parallel to R1) | M |
| R4 scope + evidence pack | R2, R3 | M |
| R5 pipeline (draft, checks, verify, repair) | R4 | L |
| R6 service + API | R5 | M |
| R7 eval | R5 | M |
| R8 prompt, R9 connections scope | R6 | M |
| R10 report view + source panel | R6 | L |
| R11 deep links + email highlight | R10 | M |
| R12 e2e + docs | all | S |

The first usable slice: R1–R6 + R8 + R10 without the source panel's email highlight (citations open the inbox
at the email); R9, R11 and R7 follow.

## Later (not in this plan)
- Reports across a whole collection or a filtered table (same pipeline, scope = a filter, map-reduce over chunks).
- Streaming the draft while it is written; export as PDF; turning a next step into a to-do (INSIGHT follow-up shape).
- Citations to attachments (needs attachment text extraction) and to calendar entries.

## Decided (open questions answered 2026-10-14)
1. **HTML-only emails:** add **jsoup** (`org.jsoup:jsoup`) for the HTML → text conversion in `ai/HtmlText` (R3); the
   README lists it with the other dependencies.
2. **Visibility:** a report is visible to every user who can see the record. Email citations are still filtered to the
   reader's own mailboxes on read (R6), so a report never shows another user's mail.
3. **Verifier model:** the same model as the draft, in a separate call that sees only one claim and its sources (R5).
