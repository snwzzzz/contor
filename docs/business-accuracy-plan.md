# Business mail accuracy — findings and implementation plan

> Measured 2026-10-13 on the running dev instance with the demo mailbox (business dataset slice 1, 305 mails, dates
> shifted +70 days), model `qwen3.5:9b`, prompt `extract-v6`, AI setup records/calendar/tasks **AUTO**, replies
> **SUGGEST**, courtesy replies on, categories discovery on. Every step below is test-first (CLAUDE.md §0) and is judged
> with the business evals (`AiBusinessExtractionEvalTest`, the business triage, `AiMailboxReplayEvalTest`).
> **Status:** steps 0–2 implemented (2026-10-13), results below under *Progress*; steps 3–9 open.

## How it was measured

The live database was dumped (proposals, every collection's records with their source mails, relations) and compared
with the labels of the dataset: the expected end state (`expected/end-state.json`) and the per-mail labels
(`mailbox/m-*.json`). Records match by key (order number, tracking number, …), else by title, else by shared source
mail. Because the demo dates were shifted, a date also counts as right when it equals the labelled date + 70 days.
A second run of the per-mail eval on the first 40 business mails (isolated, no live pipeline) separates model and
prompt errors from pipeline errors: both show the same pattern, so most problems are in the prompt and schema, a few
(state updates, duplicates) in the pipeline.

Processing itself is healthy: all 229 inbox mails analysed in 34 min (≈ 9 s per mail), no failures.

## Results

### End state (what the collections hold after the mailbox)

| Collection | Expected | Got | Matched | Main problem |
|---|---|---|---|---|
| sales_orders | 25 | 11 | 5 | customer orders filed as purchase orders; follow-ups add nothing; two numbers per order |
| purchase_orders | 7 | 16 | 6 | 10 of 16 are really customer orders ("PO HV-6120") |
| shipments | 15 | 10 | 10 | state stuck at the first mail (1/10 right); parcel notices and one truck missed |
| tasks | 35 | 21 | 1 | no "pay invoice" to-dos at all; deadlines become events; wrong direction ("order from Pichler") |
| events | 12 | 52 | 10 | deliveries, dispatches, inspections, deadlines and seminars become calendar entries |
| invoices | 11 | 9 | 9 | good; due date 5/7, direction 7/9 |
| complaints | 5 | 3 (+2 in an auto-created `quality_complaints`) | 0 | no RMA reference kept; split into a duplicate category |
| customers | 7 | 1 | 0 | relationship mails (new customer, leaving, at risk) never become customers |
| people | 51 | 40 | 38 | colleagues missing; phone in signature only 10/34 |
| organizations | 40 | 40 | 32 | fine |

Field accuracy on matched records: shipments.state 1/10, shipments.eta 4/10, sales_orders.state 2/5,
purchase_orders.state 2/6, events.starts_at 4/10, people.phone 10/34, invoices.amount 9/9, tracking/PO/invoice numbers
all correct.

### Per mail

106 of 164 labelled business mails missed at least one key record. Missed / expected per collection: sales orders
44/54, shipments 24/34, tasks 30/38, purchase orders 12/18, complaints 6/8, invoices 5/14, events 4/16. Typical:
replies in a thread ("AW: …", "goods arrived", "please book 45 instead of 30", "wir nehmen das Angebot an") produce
**nothing**.

### Replies, noise, insights, categories

- Reply drafts: 6 of 9 unanswered mails that need a reply got one (missed: Hartmann's second chase, both Sørensen
  mails — exactly the customer who was waiting). **11 noise mails got drafts** (newsletters, price alerts, seminars,
  an out-of-office) **including a draft answering the phishing mail**; 22 more routine mails got unnecessary courtesy
  drafts ("Handschuhe da", "Bestand KW 30", invoices).
- Noise produced 4 records (3 seminar ads as events, the phishing sender as an organization).
- Insights: 0 of 5 expected (Sørensen waiting, Alpen-Sammelgut late twice, Elbtal postponed twice, Kessler at risk,
  Jensen ordering less). Proposed instead: two "no answer from a colleague" rules, a clash, "Missing accommodation for
  Vossberg trip", "Missing invoice for Pichler".
- Category discovery created `quality_complaints` (overlaps complaints), `price_alerts`, `maintenance_tasks`,
  `insurance_claims`, `pallet_rentals` — mostly from noise or overlapping existing categories.

## Root causes

| # | Cause | Evidence |
|---|---|---|
| R1 | The extraction prompt calls contor "a personal management app" and never says who the user is (a company that sells to customers and buys from suppliers). | customer "PO"s as purchase orders; Ostrowski created as a supplier; "Order 50 packs … from Pichler"; "Request invoice RE-2026-0377 from Pichler" |
| R2 | `events: … deadlines with a date` — any date becomes an event. | 42 extra events: deliveries, dispatches, inspections, "Submit docs for …" |
| R3 | Existing records only get "the values they lack" (`EmailExtractionService`); a STATUS or date that is already set is never changed. | shipment/order/PO states stuck at the first mail; moved delivery dates kept |
| R4 | One order, two identifiers: the customer's reference first, our SO number later; the match key is `order_number` only. Unknown totals are written as 0; numbers are invented. | KM-26-0712 vs SO-26-0155 never joined; `0 EUR` totals; "PO-26-0924", "PO-26-0924-01" (do not exist) |
| R5 | Follow-up mails in a thread: the model does not turn "arrived", "confirmed", "book 45 instead of 30" into an update of the order it refers to. | 13 thread replies with no proposal at all, incl. the Thiele acceptance |
| R6 | `notifications without anything to record: return no items` — carrier and parcel notices are dropped. | 0/3 parcel shipments, Cierzo TC-26-40713 missed |
| R7 | Tasks: no rule turns an open incoming invoice into "pay"; titles in English for German mail; requests become events. | 0/7 payment to-dos; title match 1/35 |
| R8 | Reply candidate filter: automated senders = only `noreply`, `notifications`, `mailer-daemon`…; texts only English "unsubscribe" + a few German phrases; no subject or header check; courtesy relies on the model obeying "never for newsletters". | 11 drafts on noise incl. phishing; 22 unnecessary courtesy drafts |
| R9 | Contacts: phone numbers and roles in signatures are skipped; colleagues are not recorded. | phone 10/34; Tobias, Daniel missing |
| R10 | Insights prompt is personal-life oriented and has no business detectors; the unanswered rule only covers questions the user asked. | 0/5 business insights; "Missing accommodation …" |
| R11 | Discovery may create categories that overlap existing ones, also from noise. | `quality_complaints` next to `complaints`, `price_alerts` |
| R12 | Same thing from two mails is stored twice when titles differ; local times in another zone are converted wrongly. | dinner ×2, Vossberg damage ×2, driver typo ×2; 11:00 Istanbul stored as 14:00 Hamburg |

## Plan

Order = expected gain per effort. Each step: failing unit tests first, then the change, then the business evals; a step
is done when its target holds on two runs (results vary between identical runs) and nothing else regresses beyond the
baseline tolerance.

### Step 0 — Measurement (S)
- Commit the live comparison as `scripts/business-dataset/live-compare.mjs` (+ a `dump-live.sh` that exports proposals,
  records with sources and relations from Postgres) with node tests for the matcher; same matching as above.
- Write the first `ai-eval/business-baseline.json` (`AiBusinessExtractionEvalTest -Dai.eval.updateBaseline=true`) and
  run `AiMailboxReplayEvalTest` once (the replay uses unshifted dates, so date fields are comparable).
- Target: numbers of this document reproduced by the tools.

### Step 1 — Business context in the extraction prompt (M) — R1, R2, R6
- `EmailExtractionService`: the system prompt names the user's organization and role from a new **owner profile**
  (AI settings: company name, own domains, "we sell to customers / buy from suppliers"; for the demo prefilled from
  `world.json`), and gets collection-specific rules from the collection **descriptions** instead of hard-coded
  personal rules: a customer's order = sales order even when they call it "PO"; our own `PO-` numbers = purchase
  orders; deliveries, ETAs and pickups go to shipments (eta), deadlines to tasks (due_date); events only for
  appointments with people, calls, visits, trade fairs, unloading slots with a time.
- Carrier and parcel notices with a tracking number are records (shipments), not "notifications without anything".
- Bump `PROMPT_VERSION` to `extract-v7`; measure against two runs of v6.
- Targets (per-mail eval): purchase_orders precision ≥ 0.8, sales_orders recall ≥ 0.6, events precision ≥ 0.5,
  shipments recall ≥ 0.8; personal corpus (`AiExtractionEvalTest`) within baseline tolerance.

### Step 2 — Updates may change state and dates (M) — R3, R5
- `EmailExtractionService`: when an extracted item matches an existing record, propose UPDATE_RECORD with every value
  that **differs**, not only the missing ones — for STATUS fields only forward along the collection's state order
  (e.g. booked → picked_up → in_transit → delayed → delivered; cancelled/damaged allowed from any), for dates when the
  mail is newer than the record's last source. Values that would go backwards are dropped (logged).
- Prompt: "a reply in a thread that confirms, changes, cancels or reports arrival of something already recorded →
  return that record with the changed values" plus the thread's existing records (keys and current state) in the
  context block (`MailContextPrompt` already lists records; add their key fields and state).
- Targets: shipments.state ≥ 0.7 and sales_orders.state ≥ 0.6 in the replay; thread replies with expected records
  that produce nothing ≤ 20 % (now ≈ 80 %).

### Step 3 — Order identity and numbers (M) — R4
- Schema (`BusinessCollections`, spec of the demo; for user collections a documented pattern): sales orders get
  `customer_reference`, purchase orders `supplier_reference`; match keys become "order_number or customer_reference".
  Requires multi-key matching in `RecordService`/extraction dedupe (today: all match keys must be equal) — new rule:
  any one shared non-empty key matches.
- Guardrail in code: an identifier-like value (order, PO, invoice, tracking number) is kept only when it occurs in the
  email or its context (normalized); otherwise dropped — no invented numbers.
- MONEY/NUMBER: a 0 the email does not state is dropped (model fills unknown totals with 0).
- Targets: no invented numbers; sales orders joined across customer reference and SO number in the replay (≥ 80 %).

### Step 4 — Reply candidates and courtesy (S) — R8, security
- `ReplyCandidateFilter`: automated senders also by local part (`newsletter`, `news`, `alerts`, `digest`, `webinare?`,
  `seminare?`, `marketing`, `info` when combined with a list footer), by headers (`List-Unsubscribe`,
  `Auto-Submitted`, `Precedence: bulk` — stored in `email_header`), by subjects (`Automatic reply`, `Automatische
  Antwort`, `Abwesenheit`, `Undeliverable`, `Read:`, `Gelesen:`, `Accepted:`, `Angenommen:`) and German/English footer
  phrases (`abbestellen`, `abmelden`, `automatisch versendet`, `newsletter`).
- Courtesy drafts only for senders the user has corresponded with or knows (People record), never for first-time
  senders — decided in code, not by the model.
- No draft at all for mail that asks for payment via a link from a first-time sender (phishing pattern); later a
  proper suspicious-mail signal.
- Targets: 0 drafts on noise (now 11), unnecessary courtesy drafts ≤ 5 (now 22), recall on needed replies unchanged.

### Step 5 — Tasks that matter (S) — R7
- Rule (no model): an incoming open invoice with a due date gets a follow-up task "Rechnung <nr> bezahlen" /
  "Pay invoice <nr>" (language of the mail), due = due date or skonto date; done when a later mail or bank notice marks
  the invoice paid.
- Prompt: task titles in the mail's language, imperative; requests to send documents/goods are tasks with the stated
  deadline; never tasks to buy from a customer.
- Targets: tasks recall ≥ 0.6 (per-mail), payment to-dos 7/7.

### Step 6 — Contacts from signatures (S) — R9
- Prompt + post-processing: phone and role from the signature block of a human sender (the signature parser can be
  rule-based: last lines, `Tel.`/`+` pattern); colleagues on the own domain are people too (organization skipped).
- Target: people.phone ≥ 0.8, colleagues recorded.

### Step 7 — Business insights (M) — R10
- Rule detectors (no model) next to the existing ones: an inbound request without the owner's answer for ≥ 5 working
  days (customer waiting); the same carrier/supplier delayed ≥ 2 times within 30 days (shipment/PO state `delayed` or
  moved dates); a customer's order totals falling over consecutive orders or an explicit "skip"/"competitor" mention.
- Model pass: a business-oriented prompt variant when the owner profile says "company".
- Target: ≥ 3 of the 5 expected insights in the replay, ≤ 3 irrelevant insights.

### Step 8 — Discovery and duplicates (M) — R11, R12
- Discovery: never propose a category whose description or fields overlap an existing one (embedding similarity of
  descriptions + field overlap); ignore noise senders as evidence; default categories to SUGGEST in the AI setup.
- Dedupe in the pipeline: an event with the same participants within ±1 day, a complaint about the same order, a
  person with the same phone — match the existing record instead of creating a new one (uses the match rules of step 3).
- Time zones: the model returns local time and the zone it read separately; the conversion to an instant happens in
  code (`ZoneId` of the named zone, the user's zone otherwise).
- Targets: 0 duplicate records in the replay; events.starts_at ≥ 0.8.

### Step 9 — Re-measure and document (S)
- Full replay without date shift, per-mail eval, triage; update this document with before/after, README (*AI
  evaluation*), baselines, and the business dataset plan's notes.

## Targets summary (replay, slice 1)

| Metric | Now | Target |
|---|---|---|
| sales orders found | 5/25 | ≥ 20/25 |
| purchase orders precision | 6/16 | ≥ 0.8 |
| shipments found / state right | 10/15 · 1/10 | ≥ 14/15 · ≥ 0.7 |
| tasks found | 1/35 | ≥ 21/35 |
| events precision | 10/52 | ≥ 0.6 |
| drafts on noise / unnecessary courtesy | 11 / 22 | 0 / ≤ 5 |
| expected insights | 0/5 | ≥ 3/5 |
| duplicates | 4 | 0 |

## Notes on this measurement

- The demo import shifted mail dates by 70 days while dates written inside the mails stayed in July, which costs some
  date accuracy (e.g. "Grundreinigung 18.07." stored in September). The replay (step 0) runs unshifted.
- The whole mailbox was analysed in one run after import, so mails the owner had already answered correctly got no
  draft; live arrival order (replay) tests the draft-then-answer path.
- Title matching is strict (German labels vs English model titles); the task numbers are partly a wording effect, but
  the per-mail check (missed records per mail) confirms the gaps.

## Progress

### Steps 0–2 (2026-10-13)

Done:
- **Step 0:** `scripts/business-dataset/dump-live.sh` + `live-compare.mjs` (matcher in `lib/live.mjs`, node tests)
  reproduce the measurement above. The per-mail eval of prompt v6 is the reference (`business-baseline-v6`, not committed
  as `ai-eval/business-baseline.json` yet — see open points).
- **Step 1:** prompt `extract-v7`: no "personal app" framing; an optional **owner profile** (`ai_settings.owner_profile`,
  Settings → Autonomy "About you and your business", filled by the demo import); events only for appointments, delivery
  dates belong to orders/shipments, deadlines to tasks; carrier and parcel notices are shipment records; sharper
  descriptions of the business categories (customer "PO" = sales order). A human sender is always a person (with phone
  and role from the signature) and their company an organization.
- **Step 2:** an existing record now also gets a **forward** status change (along the field's states), changed dates
  and changed amounts; the context lists existing records with their key and status
  (`sales_orders: Kessler SO-26-0155 (order_number: SO-26-0155, state: shipped)`) and the prompt tells the model to
  return such a record with the changed values when a mail confirms, changes, cancels or reports its arrival.

Per-mail eval, 164 business mails, v6 → v7 (before the sender rule was added):

| Collection | v6 precision / recall | v7 precision / recall |
|---|---|---|
| sales_orders | 79 % / 35 % | 74 % / **54 %** |
| shipments | 85 % / 65 % | 89 % / **94 %** |
| purchase_orders | 43 % / 83 % | **57 %** / 67 % |
| events | 7.5 % / 31 % | **32 %** / 38 % |
| people | 95 % / 71 % | 94 % / **30 %** ✗ |
| organizations | 84 % / 75 % | 93 % / **29 %** ✗ |
| tasks | 5 % / 5 % | 0 % / 0 % ✗ |

The v7 prompt stopped the model from recording the sender as a contact; the rule "a human sender is always a people
item … its company an organization" was added afterwards and is **not measured yet**. Task matching needs a closer
look (`-Dai.eval.verbose=true` prints expected and produced titles per mail).

Open before steps 3+:
- Re-run the per-mail eval with the sender rule; accept v7 only when people/organizations are back at the v6 level, then
  write `ai-eval/business-baseline.json`.
- Run `AiMailboxReplayEvalTest` to completion once (it runs, ≈ 40 s per mail when sharing the GPU) for the replay
  numbers of step 2 (state updates only show in the replay, not in the per-mail eval).
- Look at the task titles/dates (per-mail 0/38).
