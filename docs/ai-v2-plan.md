# AI v2 implementation plan (smarter replies, context, importance, insights, speed)

> Living plan. Each step is done test-first (CLAUDE.md §0), follows the React best-practices skill for frontend work, and
> updates the README in the same change. Builds on `ai-plan.md` (extraction, runs) and `replies-plan.md` (threads, reply
> drafts). Nothing is ever sent; everything stays on the local Ollama.
> **Status:** steps 1–6 implemented (step 3 with mostly negative results, see notes); steps 7–9 open. Master prompt for implementation sessions: `ai-v2-master-prompt.md`.
>
> Implementation notes (where it differs from or refines the text below):
> - Step 1: `ai/AiTimings` (per-thread session, no Micrometer) is fed by `OllamaClient` and opened per email by
>   `AiAnalysisWorker`; stages are `extract` (includes the relations call), `embed`, `reply` (triage + compose). Logged as
>   `AI timings email <id>: …`. `LlmClient` is unchanged. On-demand reply suggestions are not timed.
> - Step 1 baseline (`MailReadPathIntegrationTest`, 300 emails with ~20 KB text + HTML bodies, page size 50, Testcontainers
>   on the dev machine): inbox page **6 statements, ~87–124 ms** median; thread (5 messages) **6 statements, ~37 ms**.
>   The statement counts are asserted (≤ 6); step 2 compares against the timings.
> - Step 2: option 1 (`findBy` + projection) was skipped for option 2: a repository fragment `EmailSummaryQueries`
>   (`findSummaries(spec, pageable)`, Criteria `construct` into the `EmailSummary` record, count only when needed).
>   `MailMapper.toSummaryDto` takes `EmailSummary`. Inbox page **~87–124 ms → ~41 ms** (same 6 statements, no body
>   columns, asserted with a Hibernate `StatementInspector` in the baseline test); thread unchanged (~35 ms).
> - Step 2: no new index — `ix_email_message_account_received (account_id, received_at desc)` already serves the list
>   (the folder filter is `<> 'sent'`, not an equality an index column would help with).
> - Step 2 frontend: hover-intent prefetch (120 ms, one timer in `InboxView`), neighbours prefetched on open, a
>   `ThreadPreview` from the list row while the thread loads, `keepPreviousData` for the list. The thread `staleTime`
>   stays at the global 30 s (enough for prefetch-then-click; a longer one would need invalidation after syncs, which
>   the web app has no signal for yet). No virtualization: 50 rows per page render fine.
> - Step 3 item 4 is already done: the relations call only runs for mails with two or more extracted records.
> - Step 3 measurements (eval corpus, 49 mails, RTX 3060, `qwen3.5:9b`; the eval now prints the split per call):
>   extraction call 1 212 → 205 tokens, **reading the prompt 0.8 s, writing the answer 6.9 s** (≈ 90 %); relations call
>   (14 of 49 mails) 314 → 167 tokens, 5.8 s answer. The answer length is the only lever that matters.
> - Step 3 results — what was done:
>   - `extract-v6`: only the **relations** call changed — `reason` at most 12 words, "list each relation once".
>     Relations answers 167 → 70 tokens, 5.8 s → 2.8 s per call (14 of 49 mails). The extraction prompt is unchanged:
>     short reasons there saved only 12 tokens (205 → 193) and that run found one to-do fewer, so it was not worth the
>     risk. The final run passes the gate with extraction results identical to `extract-v5`.
>   - Embedding backfill in batches of 16 (`EmbeddingClient.embedAll`, one `/api/embed` call): 48 texts 2.98 s → 1.6 s.
> - Step 3 results — what was tried or decided against, with the reason:
>   - **Compact one-line JSON** halved the answer tokens (205 → 102) but cost a lot of quality (events recall 58 % → 25 %,
>     dates 33 % → 12.5 %, people recall 77 % → 54 %): the model needs the formatting tokens. Not used; the unit test
>     guards against it coming back.
>   - **Merging triage into extraction**: would add ~20–40 answer tokens (≈ 1 s) to *every* email to save one ~1.5 s triage
>     call on the minority that passes the reply rules — slower overall. Not done; steps 5–7 still need the signals, they
>     will come from the triage call (which also needs work: precision 0.47, recall 1.00 — invoices and reminders
>     count as "needs reply").
>   - **Bulk prefilter without a model call**: the eval corpus shows that orders, invoices, trips and subscriptions come
>     from `no-reply@`/`notifications@`/`info@` senders — sender rules would drop real records. Needs real list headers
>     (`List-Unsubscribe`, `Precedence: bulk`), which Graph's delta sync does not return. Not done.
>   - **Stable prompt prefix**: already the case (system prompt, collections, then the email); reading is < 1 s anyway.
>   - **Two parallel model calls** (`OLLAMA_NUM_PARALLEL=2`): not done. Extraction deduplicates against records and
>     pending proposals; two emails about the same person processed at once would both propose that person.
> - Step 3 eval gate: the committed `ai-eval/baseline.json` fails against unchanged `extract-v5` code (organizations
>   precision 0.40 → 0.31, tasks ±1 record) — the 0.05 threshold is below the noise of small collections. The baseline
>   was not updated. The gate is now noise-aware: `BaselineComparison` judges each metric as if one record were
>   better (`-Dai.eval.tolerance`, default 1); unchanged code passes, the compact-JSON drop is still caught.
> - Step 4: `MailContextService` + `MailContextPrompt` (ADR 0009). Differences from the text below: the context is
>   rendered by the service and passed as text in `EmailContent.context` (the extraction service and the eval need no new
>   dependency); only mail received *before* the email counts (the initial analysis must not read an old email with
>   knowledge of later ones); sender history is inbox mail only (your replies come in through the conversation); the HNSW
>   index already existed (no migration). The reply composer does **not** use the new context yet — step 6 rewrites it
>   (`reply-v2`) and moves its grounding into `MailContextService` then.
> - Step 4 eval (`AiContextEvalTest`, 3 cases, each with and without context): with context 2/4 expected records, 6/6
>   fields; without 1/4, 1/2 (the reminder produced nothing). The first wording made the model rename the moved interview
>   ("… (moved)"), which would break deduplication — the prompt now asks for exactly the same title. To-dos ("pay the
>   invoice", "comment before the meeting") are missed with and without context: extraction's to-do recall is weak in
>   general (31 % in the main eval) — worth its own look before step 8 relies on to-dos.
> - Step 5: as planned, with these details: the migration is `V1_0_24` (importance_score `integer` — Hibernate maps `int`
>   to it); signals come from `ImportanceSignalService` (People record with the sender's address, `existsSentTo` over Sent
>   Items, `ReplyCandidateFilter.isAutomatedSender`) and the reply triage's urgency (until now discarded);
>   `ProposalDraft.signals` carries them to `ProposalService.submitDraft`, which scores every new proposal (no signals =
>   kind and payload only, e.g. discovered categories). Pending proposals from before are scored once at startup
>   (`scoreUnscoredProposals`). UI: importance chips *High and up* / *Urgent*, order *Newest first* / *Most important
>   first*, both in the URL; badge only for HIGH/URGENT (most proposals are NORMAL/LOW — a badge on every card is noise);
>   the reason in the sidebar's *Importance* section. Not done: a separate urgent count in the sidebar badge (optional in
>   the plan). The thresholds and weights are a first guess; tune them once real proposals show how they spread.
> - Step 6: `SlotFinder` (pure; one slot per day, half-hour starts, local working hours across DST, all-day events block
>   the day) + `AvailabilityService` (Calendar collection, `AI_WORK_*` env vars — no UI setting yet) + triage fields
>   `intent`, `questions` (≤ 5), `scheduleFrom`/`scheduleTo`, `timeOfDay` + `reply-v2` composer (`Brief`: intent,
>   questions, free times, multi-mail context without the thread; `answers` per question; open answer without a
>   placeholder drops the draft). Differences from the plan: free times are offered whenever the email asks about days,
>   not only for intent SCHEDULE (an offer request asking "which dates are you free?" was PROVIDE_INFO); a `timeOfDay`
>   was added after the eval offered 09:00 for a dinner (EVENING = 18–22); the composer may decline offered times that do
>   not fit and write a placeholder instead; on-demand suggestions now run the triage too (for questions and intent,
>   its "no reply needed" is ignored); slots are shown on the card but cannot be removed one by one (edit the text);
>   no "hold this slot" event proposal yet. The reply grounding (contacts, upcoming events/to-dos) stays in
>   `ReplySuggestionService`.
> - Step 6 eval: triage prompt ("is a written answer expected? a request to *do* something is not") — precision
>   0.47 → 0.69, recall stays 1.00 (54 cases; remaining false positives are meeting invitations whose labels are
>   debatable). `AiReplyDraftEvalTest` (5 reply cases, 2 new): 5 drafts, 10 questions found, 9 answered, 1 left open as a
>   placeholder; the dinner gets 18:00 slots, the meeting request Monday/Tuesday 09:00, the quote request three November
>   dates.
> - Consequence for step 4: extra context in the prompt is cheap (reading ≈ 1 s per 1 200 tokens), so the budget can
>   be generous; asking for more output is what costs time.

## Goal

1. **Question replies:** an email that asks the user something ("when can you attend …?", "can you send …?") gets a
   draft that answers each question, with concrete times from the user's own calendar where possible.
2. **Abstract replies:** emails that need no answer but deserve one get an optional courtesy draft — a job rejection
   ("thank you for the opportunity, please keep me in mind"), an offer to decline politely, a thank-you after help.
3. **Abstract proposals (insights):** proposals that only become visible across many emails and are not obvious from a
   single one: an unanswered question the user asked a week ago (follow up), a price increase across invoices, an
   application funnel going cold, a trip without a hotel confirmation, two appointments that clash.
4. **Importance filter:** every proposal has an importance; the proposals page filters and sorts by it.
5. **Multi-mail understanding:** the model sees the thread, the sender's recent history, semantically related emails
   and the records already linked to them — not only the email at hand.
6. **Faster email reads:** the inbox list and reading pane load faster, and the AI gets through mail faster.

## What exists already

| Piece | State |
|---|---|
| Reply pipeline | `ReplyCandidateFilter` (rules) → `ReplyTriageService` (`needsReply`, `reason`, `urgency` — urgency is **discarded**) → `ReplyComposerService` (`reply-v1`, placeholders) → `DRAFT_REPLY` proposal → `DraftReplyApplier` → `reply_draft` |
| Reply grounding | `ReplySuggestionService.grounding`: sender's People/Organization record, ≤ 5 upcoming events / open tasks matching sender name or subject |
| Thread context | `EmailMessageRepository.findThread` (same mailbox + `conversation_id`, sent included); only replies use it — **extraction reads one email alone** |
| Embeddings | `nomic-embed-text`, `email_embedding` `vector(768)`, `EmailEmbeddingService` (per email after extraction + backfill); used for semantic search only |
| Proposals | `proposal`: `kind`, `status`, `payload`, `confidence`, `reasoning`, `source_email_id`, dependencies; **no importance**; list filters only `status` + `group` (ACTIONS/FINDINGS) |
| AI worker | `AiAnalysisWorker.processEmail`: per email sequentially on one thread: extraction call (+ relations call) → embed → triage call → compose call. ~12 s per email warm (ai-plan.md) |
| Mail list | `MailService.listEmails` loads full `EmailMessage` entities (incl. `body_text`/`body_html`) for a summary page; thread stats, labels and reply markers are batched (no N+1) |
| Reading pane | `EmailThread.tsx` → `useGetEmailThread(emailId)` on selection; global `staleTime` 30 s; no prefetch |
| Eval | `ai-eval/` cases, `AiReplyTriageEvalTest`, extraction eval with `baseline.json` |

## Decisions (proposed defaults — confirm or change)

1. **Measure before optimizing.** Step 1 adds timings; every speed change in steps 2–3 states before/after numbers in its
   commit message. No change ships that makes the extraction eval worse.
2. **One "understand" call per email instead of extraction + triage.** The extraction call also returns a small `mail`
   object (needs reply, reply intent, questions, importance signal, bulk flag). The separate triage call stays only for
   on-demand use and when extraction has no targets. Saves one model call per candidate email.
3. **Importance is a 4-level enum** `LOW | NORMAL | HIGH | URGENT` plus a numeric `importance_score` (0–100) for sorting
   and an `importance_reason`. The level is derived from the score by fixed thresholds, so filtering stays simple.
4. **Courtesy replies are opt-in** within the existing `replies` permission: a new setting `courtesyReplies` (default
   **off**). They are created with importance `LOW` and never replace a real reply draft.
5. **Insights are a new proposal kind `INSIGHT`** (group ACTIONS). Approving an insight runs its attached follow-up
   actions (each a normal proposal draft: task, event, reply draft, record update); rejecting stores its `insight_key`
   so the same insight is never proposed again. Insights run in a separate periodic pass, never per email.
6. **Context is retrieved, not stuffed.** A `MailContextService` builds a budgeted context (thread, sender history,
   related emails via embeddings, linked records). Budgets are in characters derived from `num_ctx` like
   `ReplyComposerService` already does.
7. **Calendar-aware scheduling stays suggestive.** Free slots are computed from the `events` collection only (no Outlook
   calendar access); proposed times are concrete but the draft still marks them for review (the user edits before use).

---

## Step 1 — Baseline and timings (S)

- `ai/AiTimings` (or Micrometer timers if actuator metrics are already on — check; actuator exposes only `health`
  publicly, metrics stay internal): per stage `extract`, `relations`, `embed`, `triage`, `compose` → duration and
  prompt/answer token counts from Ollama's response (`prompt_eval_count`, `eval_count`, `prompt_eval_duration`,
  `eval_duration`, `load_duration`). `OllamaClient` returns them; `LlmClient` gets a `completeJsonWithStats` or the stats
  go to a listener — keep the existing signature working.
- Log one INFO line per email: stage timings and token counts, **no content** (email id only).
- Read path: a `@RepositoryTest` / integration test that seeds ~2 000 emails with realistic bodies and records query
  counts (Hibernate statistics) and timing for `listEmails` page 0 and `getEmailThread`. This is the baseline for
  step 2, not a hard assertion yet.
- README: where timings appear in the log, how to read them.

## Step 2 — Faster email reads (API + UI) (M)

Backend:
- **Summary projection without bodies.** `listEmails` / `listRecordEmails` must not load `body_text`/`body_html`.
  Options in order of preference — pick after measuring:
  1. Spring Data `findBy(spec, q -> q.as(EmailSummaryView.class).page(pageable))` with a closed interface projection
     (verify it works with the `Specification` + Spring Data version in use; write the test first);
  2. a JPQL/Criteria constructor projection in a custom repository fragment;
  3. move bodies to a 1:1 `email_body` table (`V1_0_24__email_body.sql`, lazy `@OneToOne`) — only if 1 and 2 fail.
- `MailMapper.toSummaryDto` works from the projection; reply markers/labels/thread stats unchanged.
- Check the query plan of the owner-scoped list (`account → owner` join + `ORDER BY received_at DESC`): add
  `ix_email_message_account_folder_received (account_id, folder, received_at desc)` if the plan shows a sort over the
  whole mailbox. Migration with a comment.
- `getEmailThread`: keeps loading bodies (it needs them); labels, drafts and the pending proposal already come from
  batched queries. No HTTP caching; the client caches (below).

Frontend (`components/app/mail/`):
- **Prefetch the thread** on row hover/focus and for the next/previous row when one is opened
  (`queryClient.prefetchQuery` with the generated `getGetEmailThreadQueryOptions`), so arrow-key reading is instant.
- **Show the summary immediately:** while the thread loads, render sender/subject/date from the list row
  (`placeholderData` / initial header), body skeleton below.
- `staleTime` for threads: 5 min (mail bodies do not change; invalidate on sync completion and on reply-draft changes).
- Keep previous page data while paging (`placeholderData: keepPreviousData`).
- If the list is long enough to matter, virtualize `EmailList` (measure first; 50 rows per page may not need it).
- HTML body: render `EmailHtmlBody` sandboxed iframe lazily for collapsed thread messages (only expanded ones mount).

Tests: repository/service test asserting the list query does not select body columns (Hibernate statistics or SQL
capture), mapper from projection, Vitest for prefetch on hover/keyboard and placeholder header, existing inbox tests
green. README: no new commands unless an index or env var is added.

## Step 3 — Faster AI processing (M)

In order, each measured with step 1's timings:
1. **Cheap bulk prefilter before any model call.** Extend the rules (`ai/MailRules`, pure, shared with
   `ReplyCandidateFilter`): no-reply senders, bulk markers, sender domains the user never wrote to *and* that sent
   ≥ N mails with the same subject skeleton → `processing_state = SKIPPED` with reason `bulk`, no extraction call. Keep
   an allowlist hook: a sender with a People record is never bulk. Measure skip rate on the eval corpus; the extraction
   eval recall must not drop.
2. **Merge triage into extraction** (decision 2): extraction schema gets
   `mail: { needsReply, replyIntent, questions[], importance (low|normal|high|urgent), deadline?, isBulk }`.
   `ReplySuggestionService.suggestIfNeeded` takes this result instead of calling `ReplyTriageService` when present.
   When extraction has no targets (all collection permissions OFF) but replies are on, run the triage call as today.
   Prompt `extract-v6`; extraction eval must not regress.
3. **Stable prompt prefix for Ollama's KV cache:** system prompt and collection schema first and byte-identical between
   emails (date line is per day — fine), the email last. Verify with `prompt_eval_count`/duration on the second email.
4. **Skip the relations call** when fewer than two items were extracted (check whether it already does).
5. **Embedding:** batch `/api/embed` for backfill (input array) — check `EmailEmbeddingService.backfillMissing`.
6. **Optional, measured:** `OLLAMA_NUM_PARALLEL=2` with two AI worker threads for new-mail processing. Only if VRAM
   allows both at `num_ctx` 16k (12 GB GPU); otherwise document why not. Runs stay single-threaded (progress order).

Tests: rules unit tests (bulk detection, allowlist), extraction parsing of the `mail` object (fake `LlmClient`), worker
test that a merged result skips the triage call, `OllamaClient` stats parsing with `MockRestServiceServer`. README:
new env vars, new skip reason, processing numbers.

## Step 4 — Multi-mail context (L)

- `ai/MailContext` record + `service/MailContextService.contextFor(emailId, Budget)`; owner-scoped, read in one short
  transaction, no model calls except the query embedding (already stored for the email):
  - **thread**: as today (`findThread`), cleaned with `EmailTextCleaner`;
  - **sender history**: the last 5 emails from the same address (else same non-free-mail domain) outside the thread,
    as one line each (date, subject, first 200 chars cleaned), plus the user's replies to them;
  - **related emails**: top 5 by cosine distance in `email_embedding` from the user's mailboxes within 180 days,
    excluding thread and sender history, above a similarity threshold (configurable, start 0.75; tune on eval);
  - **linked records**: records with a `record_source` to any of the above emails, and the sender's People /
    Organization record (move the lookups out of `ReplySuggestionService.grounding` into this service).
  - Budget: characters from `num_ctx` minus reserved tokens; trim in the order related → history → older thread
    messages; the email itself is never trimmed below its cleaned body limit.
- `EmailEmbeddingRepository.findNearest(ownerId, vector, since, limit)` native query (pgvector `<=>`), index
  check (`ivfflat`/`hnsw` exists? add in `V1_0_25__email_embedding_index.sql` if not).
- **Users:** extraction (`extract-v6`: "earlier emails" section; the model may now return UPDATE for a record mentioned
  in history — e.g. interview moved — instead of a duplicate), reply composer (`reply-v2`), insights (step 7).
- Make sure the embedding exists before extraction for new mail (embed first, then extract), so related emails work
  for the email itself; a missing embedding just leaves "related" empty.
- ADR `0009-multi-mail-context.md`: what goes into context, budgets, why retrieval over long context.

Tests: `@RepositoryTest` for `findNearest` (owner isolation!, threshold, exclusions), service test for budget trimming
order and owner scoping, extraction prompt building with context, eval: add 3–5 **multi-mail cases** (sequence of
emails where the right answer needs an earlier one: rescheduled interview, invoice reminder after invoice, "as
discussed" follow-up). README: context description, threshold env var.

## Step 5 — Proposal importance + filter (M)

Backend:
- `V1_0_26__proposal_importance.sql`: `importance text not null default 'NORMAL'` + `ck_proposal_importance`,
  `importance_score smallint not null default 50` (0–100, CHECK), `importance_reason text`, index
  `ix_proposal_status_importance (status, importance_score desc, created_at desc)`. Backfill existing rows: NORMAL/50.
- Domain enum `ProposalImportance` (`LOW, NORMAL, HIGH, URGENT`); API enum **`ProposalPriority`** (name must differ —
  add the pair to CLAUDE.md's enum table).
- `ai/ImportanceScorer` (pure, unit-tested) → `Importance(score, level, reason)` from:
  model signal (`mail.importance` / triage urgency, now stored), deadline proximity (≤ 2 days +25, ≤ 7 days +10),
  direct question to the user (+15), known contact / thread the user replied in (+10), kind (reply to question > task >
  event > record update > new collection; courtesy reply capped at LOW), bulk sender (−30), proposal age is **not**
  part of the score (sorting handles it). Thresholds: ≥ 80 URGENT, ≥ 60 HIGH, ≥ 35 NORMAL, else LOW.
- `ProposalService.submitDraft` takes the importance (`ProposalDraft` gets an optional `importance` component;
  default computed from kind) — keep existing call sites compiling.
- API: `GET /api/proposals?minImportance=HIGH&sort=importance|newest` and the same `minImportance` on the count
  endpoint; `ProposalDto.importance`, `importanceScore`, `importanceReason`. Approving with edits keeps importance.
- Tests: scorer table test, migration/repository filter + sort, service, controller (param validation 400).

Frontend:
- Proposals page: importance chips (All / High+ / Urgent) next to the group chips, sort toggle (Importance / Newest),
  importance badge on cards (token colors only; add `--importance-*` tokens in `tokens.css` if needed), reason in the
  detail sidebar. Filter in the URL (`?importance=high`) so it survives reloads.
- Top-bar/sidebar proposal count can show urgent count separately (optional).
- Tests: Vitest for chips → request params, badge rendering, URL state. README: filter description.

## Step 6 — Question and scheduling replies (M)

- Reply intent from step 3/4: `ANSWER_QUESTIONS | SCHEDULE | CONFIRM | DECLINE | PROVIDE_INFO | COURTESY | NONE`
  plus `questions[]` (each question as the sender asked it, ≤ 5).
- `service/AvailabilityService.freeSlots(from, to, durationHint)` over the `events` collection: working hours
  (configurable, default Mon–Fri 09:00–18:00 in `AI_TIMEZONE`), existing events as busy, returns up to 3 slots in the
  window the email asks about (or the next 10 working days). Pure slot math in `ai/SlotFinder` (unit-tested), data
  loading in the service.
- `ReplyComposerService` → `reply-v2`: gets the questions list and, for `SCHEDULE`, the free slots; rules: answer every
  listed question in order; for scheduling offer the given slots in the sender's language/format; still placeholders
  for anything the records do not answer. Output schema gains `answers: [{question, answered: bool}]`; validation:
  every question has an entry, unanswered ones must have a placeholder in the body (else drop the draft).
- Payload gains `intent`, `questions`, `proposedSlots` (spec `ProposalAction` description updated).
- UI: reply card / dialog shows the questions as a checklist (answered / placeholder) and proposed slots as chips that
  can be removed; approving with a slot may additionally create an Events proposal ("hold this slot") — optional,
  as a dependent proposal.
- Tests: slot finder (busy overlap, working hours, DST change week, all-day events), composer validation, payload,
  UI checklist. Eval: 4 cases (meeting request, two-question mail, document request, scheduling across a weekend).

## Step 7 — Courtesy and abstract replies (M)

- Setting `courtesyReplies` (`ai_settings.courtesy_replies boolean not null default false`, migration
  `V1_0_27__ai_courtesy_replies.sql`, `AiSettingsDto` / `UpdateAiSettingsCommand`, Settings → Autonomy toggle under
  "Reply drafts": "Also suggest polite replies to emails that need no answer (rejections, thank-yous)").
- Triage/understand result `replyIntent = COURTESY` with `courtesyKind`: `REJECTION_THANKS`, `DECLINE_OFFER`,
  `THANK_YOU`, `CONGRATULATE`, `KEEP_IN_TOUCH`. Rules: never for bulk/no-reply senders (a rejection from
  `noreply@jobs…` has no human to answer → no draft), never when the user already replied, at most one per
  conversation.
- Composer guidance per kind (short, warm, no new commitments; rejection: thank, express continued interest, ask to be
  kept in mind; optional "[feedback request]" placeholder the user can delete).
- Importance capped at LOW; card labelled "Optional reply".
- Tests: rules (no-reply rejection → none, human recruiter → draft), composer per kind with fake LLM, settings
  round-trip. Eval: 4 courtesy cases incl. one no-reply rejection (must produce nothing).

## Step 8 — Insights: abstract proposals across mail (L)

- `ProposalKind.INSIGHT` (+ `ck_proposal_kind` migration `V1_0_28__insights.sql`, API `ProposalAction` + payload
  description, group ACTIONS). New table `insight_dismissal (user_id, insight_key, dismissed_at)` with
  `uq_insight_dismissal_user_key`, or reuse rejected proposals' `payload.insightKey` with an index — prefer the
  query on `proposal` if it stays simple.
- `service/InsightService` (periodic, `app.ai.insights.interval`, default daily; plus "Look for insights" button in AI
  settings), runs on the AI executor after new-mail processing, never during an analysis run:
  1. **Deterministic detectors first** (cheap, testable, no model): unanswered outgoing question (sent mail with `?`
     and no reply after N days → follow-up), unreplied inbound with reply draft dismissed but deadline near, clashing
     events, recurring sender with changed amounts (MONEY fields in records from the same organization).
  2. **One model pass** over a compact digest of the last 14 days (clusters by sender/organization/topic using
     embeddings; per cluster: one-line summaries + linked records + open tasks/events). Prompt `insight-v1` asks for
     non-obvious observations with: `title`, `observation`, `why it matters`, `evidenceEmailIds`, `confidence`,
     `novelty` (self-rated), `followUps` (each one of the existing draft kinds: task, event, record update, reply
     draft).
  3. **Guardrails:** evidence ids must exist and belong to the user (else drop); drop insights whose follow-ups
     duplicate a pending proposal for the same emails; drop `novelty < 0.5`; `insightKey` = hash of kind + sorted
     evidence ids + normalized title; skip dismissed keys; max 5 insights per pass.
- `InsightApplier` (`@Component ProposalApplier`): approving submits the (possibly edited) follow-ups as normal
  proposals at SUGGEST level, linked as dependents; result `{ followUpProposalIds }`.
- Importance via `ImportanceScorer` (insights with a deadline in evidence can be HIGH).
- UI: `InsightCard` (observation, why, evidence emails as links to `/inbox?email=`, follow-up list with checkboxes
  → approving sends only the checked ones as edited payload).
- Tests: each detector, guardrails (foreign evidence id dropped, dismissed key skipped, duplicate follow-up dropped),
  applier, card. Eval: 3 insight scenarios as multi-mail cases (score only "found / not found", report only).
- README + architecture.md: insight pass, interval env var, what detectors exist.

## Step 9 — Eval and docs wrap-up (S)

- `ai-eval`: cases from steps 4, 6, 7, 8; update `baseline.json`; document how to run.
- `architecture.md`: AI pipeline diagram (rules → understand → context → proposals → importance → insights).
- CLAUDE.md: enum pair `ProposalImportance` → `ProposalPriority`; new kind `INSIGHT` in proposal rules.
- Mark this plan's status per step.

---

## Security and privacy (all steps)

- Everything stays on the local Ollama; no new OAuth scopes; nothing is sent.
- Context and insight queries are always owner-scoped (`ownerId` mandatory, tests for cross-user isolation in every
  new repository method — especially `findNearest`).
- Never log prompts, bodies, drafts, questions or full addresses; timings log ids and numbers only.
- Evidence ids from the model are untrusted input: validated against the user's emails before use.

## Order and size

| Step | Content | Size | Depends on |
|---|---|---|---|
| 1 | timings + read-path baseline | S | – |
| 2 | faster reads (projection, index, prefetch) | M | 1 |
| 3 | faster AI (bulk skip, merged triage, prompt prefix) | M | 1 |
| 4 | multi-mail context | L | 3 |
| 5 | importance + filter | M | 3 (signal), 4 optional |
| 6 | question / scheduling replies | M | 3, 4 |
| 7 | courtesy replies | M | 6 |
| 8 | insights | L | 4, 5 |
| 9 | eval + docs | S | all |

Each step ends green (`./mvnw test`, `npm test`, `npm run lint`, `npm run typecheck`, e2e where flows changed) with
the README updated and this plan's status line updated.

## Open questions

1. "Make email reads faster": the plan covers both the inbox UI/API (step 2) and the AI's reading throughput (step 3).
   If only one matters, drop the other.
2. Working hours for `AvailabilityService`: a setting in the UI, or env vars only?
3. Insights cadence: daily is the default; on every new-mail batch is possible but costs one long model call each time.
