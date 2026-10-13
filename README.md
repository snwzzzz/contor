# contor

Personal, self-hosted management platform: dynamic collections (Deliveries, Orders, People, …) with relations between records, an Outlook inbox sync, and a local AI assistant (Ollama) that analyzes the inbox, creates matching categories and keeps its own calendar and to-do list.

> **Status: Phase 2 (local AI).** Backend: security, collection engine, labels, rules, relations, proposals, saved queries, Outlook mail sync, and the AI pipeline (Ollama client, permissions, inbox analysis runs, reply drafts that are never sent). Frontend: setup/login, app shell, inbox with conversation threads and reply drafts, AI setup wizard, proposals review, calendar and to-dos. The collection UI and TOTP 2FA follow. Plans: `docs/ai-plan.md`, `docs/replies-plan.md` (threads and AI reply drafts, never sent), `docs/ai-v2-plan.md` (smarter replies, multi-mail context, proposal importance, insights, speed).

## Repository layout

| Path | What |
|---|---|
| `api-spec/openapi.yaml` | API contract, single source of truth for backend and frontend |
| `contor-api/` | Spring Boot 4.1 backend (Java 21, Maven wrapper) |
| `contor-web/` | Next.js 16 frontend (App Router, TypeScript, Tailwind) |
| `infra/docker-compose.yml` | PostgreSQL 18 + pgvector, bound to `127.0.0.1` |
| `docs/` | architecture and decision records |
| `CLAUDE.md` | working rules and conventions |

## Prerequisites

- Java 21 (no Maven install needed, use `./mvnw`)
- Node.js 24+ and npm 11+
- Docker (Docker Desktop or compatible): Postgres for dev, Testcontainers for backend tests
- [Ollama](https://ollama.com) running natively on the host (bound to localhost, the default) for the AI assistant; see *Local AI*

Optional, faster backend tests: let Testcontainers reuse the Postgres container between runs:

```bash
echo "testcontainers.reuse.enable=true" >> ~/.testcontainers.properties
```

## First-time setup

```bash
cp .env.example .env
```

Edit `.env` and set at least `DB_PASSWORD` and `APP_ENCRYPTION_KEY`:

```bash
openssl rand -base64 32
```

`.env` is gitignored; never commit secrets. Keep `APP_ENCRYPTION_KEY` safe: without it, stored OAuth tokens and TOTP secrets cannot be decrypted. The backend refuses to start if the key is missing or not a 256-bit base64 key.

## Run

**1. Database**

```bash
docker compose --env-file .env -f infra/docker-compose.yml up -d --wait
```

**2. Backend** (http://127.0.0.1:8080). The backend reads the repo-root `.env` itself when started from `contor-api` (IntelliJ run button or `./mvnw spring-boot:run`); values must not be quoted, and real environment variables take precedence. Exporting it first also works:

```bash
cd contor-api && set -a && . ../.env && set +a && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Alternatively, run without compose against a throwaway Testcontainers Postgres (still needs `APP_ENCRYPTION_KEY` exported):

```bash
cd contor-api && set -a && . ../.env && set +a && ./mvnw spring-boot:test-run
```

**3. Frontend** (http://localhost:3000). `/api/*` is proxied server-side to the backend (`CONTOR_API_URL`), so the browser only ever talks to one origin.

```bash
cd contor-web && npm install && npm run dev
```

Production build: `CONTOR_API_URL` is baked into the `/api` rewrite at **build** time (Next.js resolves rewrites when building), so set it for `npm run build` as well as for `npm run start`:

```bash
cd contor-web && CONTOR_API_URL=http://127.0.0.1:8080 npm run build && CONTOR_API_URL=http://127.0.0.1:8080 npm run start
```

On first start open http://localhost:3000: without a user you are sent to **/setup** (token from the backend log, see below), afterwards to **/login**.

Swagger UI for the contract (dev profile only, requires login): log in through the web app, then open http://localhost:8080/swagger-ui/index.html. Use the same host name (`localhost`) for both, since the session cookie is bound to the host name.

## First-run setup

There are no default credentials. On the first start with an empty database the backend prints a one-time **setup token** to its log:

```
==================================================================
 contor first-run setup: no user exists yet.
 ...
   <43-character token>
==================================================================
```

Use it once to create the owner account (the setup page in the web app follows in step 6; until then via `POST /api/setup`, see the API contract). The token lives only in memory: it is gone after use, and a restart issues a new one as long as no user exists. Once the owner exists, `/api/setup` is permanently disabled.


### Dev account

With the `dev` profile the backend keeps a login ready: set `DEV_ACCOUNT_USERNAME`, `DEV_ACCOUNT_PASSWORD` (same password rules) and optionally `DEV_ACCOUNT_EMAIL` in `.env`. On every start it is created as OWNER if missing, otherwise its password is reset to the configured one and any lockout cleared. First-run setup is then skipped, and the web app signs in as this account by itself: the login page asks the backend (`GET /api/auth/dev-login`, server-side) and calls `POST /api/auth/dev-login`, which exists only with the `dev` profile and answers loopback requests only. Log out to see the login page again (it signs you straight back in). Never set these outside local development.

**Demo mailbox.** To try contor on realistic business mail without connecting Outlook, set `DEMO_MAILBOX_ENABLED=true`
(dev profile, needs the dev account above). On the next start the backend imports the fictional wholesaler mailbox of
the AI evaluation (`ai-eval/business`, see *Business mail dataset*) into the dev login once: two accounts
`alex.morgan@nordhafen.example` and `orders@nordhafen.example` (shown as **Demo** under Settings → Mail accounts, never
synced), 305 messages with their threads, and the business categories (customers, suppliers, sales/purchase orders,
shipments, complaints, invoices). Then run the AI setup as usual and the background processing analyses the mails.

- The mails are from July 2026. In the AI setup pick **1 year** of history; with the default 90 days the first week is
  skipped. Reply drafts are only suggested for mail of the last 14 days: set `DEMO_MAILBOX_SHIFT_TO_TODAY=true` to move
  all dates by whole weeks so the newest mail is recent (dates written inside the mails keep their July values).
- To start over, disconnect the two demo accounts and delete them in the database, or reset the database; the import
  runs again for a dev login without demo accounts. `DEMO_MAILBOX_PATH` points to another generated mailbox folder.
## Security overview

- Session cookie `CONTOR_SESSION` (HttpOnly, SameSite=Strict, `Secure` with `APP_SECURE_COOKIES=true`), stored in Postgres. 30 min idle timeout, 12 h absolute lifetime, at most 3 sessions per user (the oldest is ended), session id rotated on login.
- CSRF: the readable `XSRF-TOKEN` cookie must be echoed as `X-XSRF-TOKEN` header on every mutating request (`GET /api/auth/csrf` sets it). No CORS: the browser only talks to the Next.js origin.
- Passwords: Argon2id; 12–128 characters with lowercase, uppercase, digit and special character, not a common password.
- Brute force: 5 failed logins lock the account for 15 min (doubling on repeated lockouts, max 24 h); max 10 login requests per minute per IP. Unknown user, wrong password and locked account return the same error.
- Everything except login, CSRF bootstrap, setup (while pending) and `/actuator/health` requires a session.
- Audit log: logins, failures, lockouts, logouts, setup, password changes, collection schema changes, proposal decisions and AI auto-applied changes, AI permission changes, analysis starts/cancels (later: 2FA).

## Database

- All fixed tables are created by Flyway migrations in `contor-api/src/main/resources/db` (`V1_0_N__description.sql`), applied automatically on backend startup. Hibernate only validates (`ddl-auto=validate`).
- Never change an applied migration; add a new one.
- Generated collection tables (`c_<slug>`) are not migrations; the collection engine creates them at runtime.
- `audit_log` and `collection_schema_change` are append-only (a trigger rejects `UPDATE`/`DELETE`).
- Columns ending in `_enc` are AES-256-GCM encrypted by the backend (`v1:` prefix = key version).
- Reset the local database (deletes all data):

```bash
docker compose --env-file .env -f infra/docker-compose.yml down -v
```

## Collections (dynamic data)

A collection (e.g. *Deliveries*) is metadata in `collection`/`collection_field` plus a generated table `c_<slug>` that holds its records. Field types: `TEXT`, `LONG_TEXT`, `NUMBER`, `MONEY`, `DATE`, `DATETIME`, `BOOLEAN`, `ENUM`, `STATUS`, `URL`, `EMAIL`, `PHONE`, `RELATION`, `FILE` (value shapes and options are documented in `api-spec/openapi.yaml`, schema `CollectionFieldType`).

- Creating a collection, adding and archiving fields is OWNER-only. Each change runs the generated DDL, bumps the collection's `schema_version`, and writes `collection_schema_change` and the audit log in one transaction.
- Archived fields are hidden and read-only; their column and data stay.
- Records: `GET/POST /api/collections/{id}/records`, `GET/PUT/DELETE .../records/{recordId}`. Filters: `?filter=eta:lt:2026-03-01&filter=carrier:eq:DHL&sort=-eta&page=0&size=25` (operators `eq ne lt lte gt gte contains empty notempty`); `?q=` searches the title and every text-like field case-insensitively.
- In the app, `/collections/{id}` shows the records as a table: global search, filters per column (text contains, choice, yes/no, number/date ranges), sorting by clicking a header, paging. The table has rows per page, column visibility (remembered per collection in this browser) and numbered paging with *Go to page*. Double-click a row, press Enter or use its open button to get the record page: a full-width banner in the dominant color of its picture, the picture itself (Outlook contact photo of its email address, otherwise the logo of its website/email domain fetched from the web; never a free-mail provider's logo) and tabs for **Details**, **Emails** (where the record comes up: emails it was found in, emails from its addresses or its domain — `GET /api/collections/{id}/records/{recordId}/emails`, scoped to your mailboxes) and **Connections** (related records).
- On first start contor seeds the system collections **People**, **Organizations**, **Events** (the assistant's calendar) and **Tasks** (its to-do list) and the relation types `works_at` (People → Organizations, inverse `employs`), `knows` (People ↔ People) and `attends` (People → Events).
- The read-only database role `app_readonly` gets `SELECT` on every generated table (for user-written SQL in a later phase).

## Local AI (Ollama)

The assistant runs entirely on this computer. Model choice for an RTX 3060 (12 GB): **`qwen3.5:9b`** for discovery and
extraction (one resident model, reliable JSON, German and English) plus `nomic-embed-text`; details and alternatives in
`docs/decisions/0007-local-ai-with-ollama.md`.

```bash
ollama pull qwen3.5:9b
```

```bash
ollama pull nomic-embed-text
```

(Or use the *Download missing models* button in the setup wizard.) Then open the app and click **Set up AI** in the top
bar (or Settings → AI Models). The wizard walks through:

1. **Welcome** – what the assistant does; nothing leaves the machine.
2. **Model** – checks that Ollama runs and the models are installed, downloads missing ones.
3. **Permissions** – read the mailboxes (off until you allow it), how far back (30/90/365 days or everything), and
   *Off / Ask me / Automatic* for new categories, contacts & records, calendar and to-dos, and *Off / Ask me* for
   **reply drafts** (default off; there is no *Automatic*: drafts are never sent), and optionally **About you and your
   business** (`ownerProfile`, also under Settings → Autonomy): a few sentences such as "Head of Operations at a
   wholesaler; customers order from us, we buy from suppliers". The extraction prompt (`extract-v7`) reads it to know
   whose side an email is on, e.g. that a customer's "PO" is an order you receive. The demo mailbox fills it in.
4. **Start** – saves the permissions and starts the analysis.
5. **Analysis** – live progress. With categories on *Ask me* the run pauses after discovery so you can create or skip
   each suggested category (e.g. *Job applications*, *Orders*), then continues email by email.

- *Ask me* stores proposals: extracted people, organizations and category records wait on **`/findings`** (keep/dismiss),
  actions such as calendar entries, to-dos and new categories on **`/proposals`** (approve/reject, filterable by kind and by
  importance — *High and up* / *Urgent*, with *Newest first* / *Most important first*, kept in the URL as
  `?importance=high&sort=importance`; important cards carry an *Urgent* / *High* badge whose tooltip says why; the cards of a row have the same height; a click opens the **detail sidebar** with every value, a before/after view for updates, the full reasoning, model/prompt version and the email it came from, and approve/reject there; record proposals can be edited there before approving (*Edit*: inputs of the collection's fields, sent as the edited payload → `EDITED_AND_APPROVED`)). Findings open the same sidebar (Keep/Dismiss, editable too). Proposal, finding and to-do cards share one hover (primary border, shadow; `lib/card-hover.ts`). *Automatic* applies
  them directly (audited; if applying fails the proposal waits for review instead).
- **Relations**: besides records, the assistant proposes relations between them ("Anna works at Acme", using the existing relation types such as *works at*). They appear as findings nested under the record they start from (relations between records that already exist are listed on their own, grouped by relation type with *Keep all* / *Dismiss all*, and filterable as *Connections*) and can only be kept once the records they connect are decided; dismissing a record dismisses its pending relations. With *Automatic* for records they are created as soon as their records exist. The record's Connections tab describes each one in a sentence with its attributes, period and whether it was found in an email; AI-created relations carry a small bot badge there. Relations follow the *contacts & records* permission.
- The assistant's own calendar and to-do list are the system collections **Events** (`/calendar`: click a day for its agenda and to add an event, click an event to open it) and **Tasks**
  (`/tasks`: a To do / In progress / Done board with fixed-height columns that scroll on their own; drag cards between and within columns (a slot with the whole card opens where it will land, at the pointer; the order is kept per to-do in `extra.boardPosition`, to-dos never moved come first by deadline; the drag logic is the reusable `components/app/board/KanbanBoard`); cards show the deadline relative to today; a click opens the **detail sidebar** where title, deadline, priority and notes are editable (explicit *Save*, only changed fields are sent; the form is built from the collection's fields, so new fields show up without code), with Start / Mark done, the emails it came from and *Delete*; closing with unsaved changes asks first); people and organizations go to **People**/**Organizations**; duplicates are matched by match keys or title.
- Dates: every extraction call gets today's date, weekday and your time zone (`AI_TIMEZONE`); relative dates in a mail
  ("Friday", "by Thursday") are resolved from the date the mail was sent. To-dos whose deadline already passed are
  dropped (old mail in the first analysis), past calendar events are kept as history.
- Expect ~12 s per email on a 3060 (the first call after a cold start ~20–50 s): the first analysis of a busy inbox runs
  for hours in the background and can be stopped at any time. Afterwards new mail is processed every 2 minutes.
- **Insights:** once a day (and on *Look for insights* on `/proposals`) the assistant looks for what only shows across
  several emails and proposes it as an **Insight** card: what it noticed, why it matters, links to the source emails and
  follow-ups (to-dos, appointments) you can untick before *Keep*; *Dismiss* makes sure the same insight never comes back.
  Rules find unanswered questions you sent (no answer for 4+ days) and overlapping appointments; one model pass over
  the last two weeks (one line per email, plus your open to-dos and upcoming appointments) finds price changes,
  promised answers that never came, trips without accommodation and the like. Its answer is checked: the source emails
  must be in the digest, novelty ≥ 0.5, confidence ≥ 0.6, at most 5 per run. Needs mail access and to-dos or calendar
  allowed; never during an analysis run. `POST /api/ai/insights` (202) starts a pass, `GET /api/ai/insights` says
  whether it still runs. `AiInsightEvalTest` (`ai-eval/insights`, report only) checks the model pass on three scenarios.
- **Multi-mail context:** the assistant reads each email together with what your other mail says about it (ADR 0009): the earlier messages of the conversation (your replies included), up to 5 earlier emails from the same sender, up to 5 related emails found through the search index, and the records already created from them — only mail received before the email. So a "can we move the interview to Monday, same place?" updates the existing interview with the right place, and a payment reminder without invoice number finds its invoice. `AiContextEvalTest` (part of `-Pai-eval`) runs the cases in `ai-eval/context` with and without that context and prints both scores.
- **Search index:** every inbox email gets a vector (`nomic-embed-text`) for semantic search, made from subject, sender and the cleaned body (no quoted history or signature; ADR 0008). New mail is embedded right before its analysis (so related mail can be found for it); mail that already exists, and all mail after a change of `AI_MODEL_EMBEDDING`, is embedded in the background (16 emails per model call, about twice as fast as one call each) shortly after start and then every processing interval. Progress shows under Settings → AI Models and in `GET /api/ai/status` (`embeddings`). The old model's vectors are removed once the new ones are complete.
- **Reply drafts** (`replies = SUGGEST`, `docs/replies-plan.md`): after an inbox email was analyzed, cheap rules skip mail
  that needs no answer from you (your own mail, no-reply/notification senders, automated texts, mail where you are only
  in Cc, conversations you already answered or that have a newer email, mail older than 14 days); a short triage call
  then decides whether it needs a reply, and the model writes a draft in the language and tone of the thread, grounded
  in the thread (your own sent messages marked as yours) and your records (the sender's contact and organization,
  upcoming appointments and open to-dos that mention them). It never invents facts: what you must decide is a
  `[placeholder]`. Since `reply-v2` the triage also names what the reply has to do (schedule, answer questions,
  confirm, decline, provide information), the questions it must answer and, for meeting requests, the days and time of
  day asked for: the draft answers every question (or leaves a placeholder for it — a question reported open without
  one drops the draft), and for meeting requests it offers up to three free times from your **Calendar** collection
  inside your working hours (`AI_WORK_*`, evenings 18:00–22:00 for e.g. a dinner; one slot per day; the Outlook
  calendar is not read). It also reads your earlier mail with the sender and related mail (ADR 0009). The card shows
  the questions with *answered* / *still open* and the offered times. Bills, payment and appointment reminders and
  requests to *do* something (pay, attend, take a test) count as not needing an answer. With *Also suggest polite
  replies* (Settings → Autonomy and the setup wizard, under *Reply drafts*; off by default, `courtesyReplies`) the
  assistant also drafts an **optional reply** to personal mail that needs no answer but usually gets one — a job
  rejection (thank them, stay in touch), an offer you decline, thanks for help, good news to congratulate on; never to
  automated senders, never while a real reply waits in the conversation, always LOW importance, labelled *Optional
  reply*. The draft is a `DRAFT_REPLY` proposal (*Replies* on `/proposals`, and at the bottom of the thread):
  **Edit & keep** opens the editor (*Keep* waits until every placeholder is filled in, *Keep anyway* does not), *Dismiss*
  rejects it. A kept draft shows in the thread as *Draft — not sent* with **Copy** and **Open in mail app** (`mailto:`,
  only when short enough) — **nothing is ever sent or written to your mailbox**. A pending draft is rejected
  automatically after a sync when you answered in Outlook or a newer email arrived (audited as `PROPOSAL_SUPERSEDED`).
  A failed draft never fails the email; the thread offers **Suggest a reply** to ask for one on demand. Inbox rows show
  *Reply suggested* / *Draft ready*.
- Permissions can be changed any time under Settings → Autonomy; *Analyze inbox again* lives under Settings → AI Models.

| Endpoint | Purpose |
|---|---|
| `GET /api/ai/status`, `POST /api/ai/models/pull` | Ollama reachable? models installed? download missing (OWNER) |
| `GET/PUT /api/ai/settings` | the current user's permissions (PUT OWNER; `completeOnboarding` ends the wizard) |
| `POST /api/ai/analysis`, `GET /api/ai/analysis/latest`, `POST /api/ai/analysis/{id}/continue\|cancel` | inbox analysis runs (mutations OWNER) |
| `GET /api/ai/eval/export?emailIds=` | OWNER: your emails with their applied/rejected record proposals as eval cases (see *AI evaluation*) |
| `POST /api/emails/{id}/reply-suggestion` | ask for a reply draft for one of your emails now (202; runs on the AI thread, the thread's `replySuggestion` shows RUNNING/FAILED; 409 when reply drafts are off) |
| `PATCH /api/reply-drafts/{id}`, `POST /api/reply-drafts/{id}/discard` | edit or discard a kept draft (your mailboxes only; 404 otherwise) |

### AI timings

The backend logs one line per analyzed email (`AiAnalysisWorker`, INFO) with where the time went: each stage
(`extract`, `embed`, `reply`) with its duration and every model call in it as Ollama reports it — prompt → answer
tokens, model load, prompt reading and answer writing time. Numbers and the email id only, never mail text:

```
AI timings email 812: extract 9.41s [qwen3.5:9b 2140→310 tok, load 0.00s, prompt 1.12s, answer 7.90s; qwen3.5:9b 900→20 tok, …], embed 0.12s [nomic-embed-text 512→0 tok, …], reply 3.02s [...], total 12.60s
```

A high `load` means Ollama reloaded the model (raise `OLLAMA_KEEP_ALIVE`, avoid model swaps); `prompt` grows with the
prompt length, `answer` with the answer tokens. On-demand reply suggestions are not timed. Measured on an RTX 3060
(`qwen3.5:9b`): writing the answer is about 90 % of a call (≈ 20–30 tokens/s), reading a 1 200-token prompt under a
second — shorter answers are what makes processing faster, a shorter prompt barely matters.

The inbox read path has a baseline test, `MailReadPathIntegrationTest` (part of `./mvnw test`): an inbox page and a
thread over 300 emails with realistic bodies must stay at a fixed number of SQL statements (no N+1), and their median
time is logged as `Read path baseline …`; the list query must not read `body_text`/`body_html`.

### AI evaluation

Every change to the prompts, the model or the extraction is judged with a repeatable score instead of a feeling.
`AiExtractionEvalTest` (`@Tag("ai-eval")`, excluded from `./mvnw test`) runs `EmailExtractionService` against the real local
Ollama over a labelled corpus with a fixed schema (the seeded system collections plus *orders*, *invoices*, *subscriptions*,
*applications*) and scores:

- record **precision and recall** per collection (match by match key, else similar title) and for **relations** (found when both records were extracted and the same relation type connects them),
- **field accuracy** per field (normalized: dates and time zones, money, phone numbers, case/accents),
- **date correctness** of events and to-dos (the dates of missed records count as wrong),
- the **false-positive rate** on mails that should produce nothing, records that must never be produced, and **latency** p50/p95.

```bash
cd contor-api && ./mvnw verify -Pai-eval
```

`AiReplyTriageEvalTest` scores which mails get a reply draft (precision/recall of "needs a reply"), and
`AiReplyDraftEvalTest` writes the drafts for the `ai-eval/replies` cases — triage, free times from an empty calendar,
`reply-v2` — prints them to be read and counts questions found, answered and left open, and whether scheduling drafts
name an offered day, and runs the `ai-eval/courtesy` cases (rejection by a person, thanks, good news must get a polite
draft; a rejection from a no-reply address must get none) — all report only.

Needs Ollama with the extraction model running; about 6 minutes for 49 mails on an RTX 3060. Relations are extracted in a second, short call (only for mails with two or more extracted records): listing relation types in the extraction prompt itself made the model invent organizations and events just to relate them (measured with this eval: 31 instead of 12 organizations). Each case is analyzed as if
processed when it was received, so "Friday" resolves from the mail date. The report goes to
`contor-api/target/ai-eval/report-<model>-<promptVersion>.json` and `.md`. The run is compared with the committed
`ai-eval/baseline.json` and **fails when a quality metric drops by more than the threshold** (default 0.05, absolute;
latency never fails a run). The run also prints `[ai-eval] where the time goes – …`: per call the prompt → answer
tokens and the seconds for loading, reading and answering, separately for the extraction and the relations call.
Results vary between identical runs (a single record more or less moves a small collection like *tasks* by ~0.08):
the gate therefore tolerates one record per metric (`ai.eval.tolerance`); for a prompt change, also compare against two runs of the old prompt.

| Option | Meaning |
|---|---|
| `-Dai.eval.model=<name>` | model to evaluate (default `qwen3.5:9b`) |
| `-Dai.eval.threshold=0.1` | allowed drop per metric |
| `-Dai.eval.tolerance=0` | records per metric given the benefit of the doubt (default 1): a metric only regresses when it is still worse with that many records better — one record moves a small collection by more than the threshold between identical runs |
| `-Dai.eval.updateBaseline=true` | write this run as the new `ai-eval/baseline.json` (commit it together with the prompt/model change that earned it) |
| `-Dai.eval.baseline=<file>`, `-Dai.eval.private=<dir>` | other baseline file / private corpus folder |

**Cases** are JSON files: `{id, email: {mailbox, fromName, fromAddress, to, subject, receivedAt, body}, expected: {records: {<collection slug>: [{title, values}]}, relations: []}, mustNotProduce: [{collection, title?}]}`;
`values` lists only the fields worth checking. The 49 synthetic German/English cases live in
`contor-api/src/test/resources/ai-eval/synthetic/` (a plain unit test, `SyntheticCorpusTest`, validates them against the eval
schema). To add one, add a JSON file there. Dates in synthetic mails are relative to `receivedAt`; mind the time-zone offset
(Europe/Berlin: +02:00 until 25 Oct 2026, then +01:00).

**Real mail (private corpus).** Cases built from your own mail go into `ai-eval/private/` at the repo root; that folder is
git-ignored and **must never be committed or shared**. To label real cases: correct the findings of an email in the UI (edit,
approve, reject), call `GET /api/ai/eval/export?emailIds=12&emailIds=13` as the owner (e.g. from the Swagger UI in the dev
profile) and save each case of the response as a `.json` file in `ai-eval/private/`. Applied proposals (with your corrections)
become `expected`, rejected ones `mustNotProduce`. The private cases are scored together with the synthetic ones.

**Reply triage.** `AiReplyTriageEvalTest` (same profile) scores which emails would get a reply draft (candidate rules,
then the triage call) as **precision/recall** against the optional `needsReply` label of each case: the synthetic corpus,
the reply cases in `contor-api/src/test/resources/ai-eval/replies/` and your private cases (add `"needsReply": true|false`
by hand). It prints the score and the wrongly decided cases; `-Dai.eval.reply.minPrecision=0.8` /
`-Dai.eval.reply.minRecall=0.8` turn it into a gate. The drafts themselves are judged by reading them.

### Business mail dataset

A fully fictional wholesaler's mailbox (Nordhafen Industriebedarf GmbH, Hamburg; only `*.example` domains, phone numbers
in a `5550` pattern, no bank details) for evaluating contor on business mail: orders, shipments across Europe, a driver
shared by two carriers, escalations, churn, meetings, to-dos, finance and noise. The first slice covers four weeks
(6–31 July 2026): 305 messages in 167 threads, 66 % German / 34 % English, 164 labelled business mails, 63 noise mails.
Plan and generator prompts: `docs/business-dataset-plan.md`, `docs/business-dataset-prompts.md`; why it is built this
way: ADR 0010.

```
contor-api/src/test/resources/ai-eval/business/
  world.json          fictional companies, people, number formats, deny list of real brands
  storylines/*.json   the source of truth: storylines of beats with facts, labels and the rendered mail
  noise/*.json        newsletters, notifications, a phishing mail, …
  mailbox/m-*.json    generated: one message each (an eval case plus account, folder, thread, reply/importance labels)
  expected/           generated: end state of every collection after the mailbox, expected insights
  audit.json          result of the independent label audit; manifest.json: generated, counts and checksums
```

Change a storyline, never a generated file, and rebuild (Node 24, no dependencies):

```bash
node scripts/business-dataset/build.mjs
```

`--check` only runs the validators (fictional data, declared numbers, time order, every labelled value stated in the mail
or a mail it depends on, language, schema); `build.mjs` refuses to write while one fails. The generator's own tests run
with `node --test scripts/business-dataset/test/`, and `BusinessCorpusTest` (part of `./mvnw test`) guards the committed
mailbox. `scripts/business-dataset/audit.mjs prepare|compare` runs the label audit (inputs without labels for a separate
labelling pass, then the agreement rates).

To judge a **running instance** that analysed the demo mailbox (see *Demo mailbox*), dump its database and compare it
with the labels — per collection (expected / got / matched / duplicates), field accuracy, the records each mail should
have produced, reply drafts (also on noise) and insights; a date shift of the demo import is detected and allowed for:

```bash
scripts/business-dataset/dump-live.sh /tmp/live && node scripts/business-dataset/live-compare.mjs /tmp/live
```

Evaluations on it (all `-Pai-eval`, report only unless noted):

| Test | What | Time (RTX 3060) |
|---|---|---|
| `AiBusinessExtractionEvalTest` | extraction with the business schema, earlier mail as context; per category and edge-case tag; gate against `ai-eval/business-baseline.json` | ≈ 35 min |
| `AiReplyTriageEvalTest#business_mailbox_triage…` | needsReply precision/recall, intent, courtesy kinds, questions found | ≈ 10 min |
| `AiReplyDraftEvalTest#business_drafts…` | drafts for business mail, printed to be read | ≈ 5 min |
| `AiImportanceEvalTest` | the importance rules against the labelled bands (no model) | seconds |
| `AiMailboxReplayEvalTest` | the whole mailbox through the real pipeline in time order (Docker + Ollama), compared with the expected end state, insights and reply labels | ≈ 45 min |

| Option | Meaning |
|---|---|
| `-Dai.eval.business.limit=n` | extraction eval on the first n mails only (no baseline comparison) |
| `-Dai.eval.business.baseline=<file>` | other business baseline file |
| `-Dai.eval.business.drafts=n` | number of business drafts (default 15) |
| `-Dai.eval.replay.limit=n` | replay only the first n days |

## Other resources (API overview)

| Area | Endpoints | Notes |
|---|---|---|
| Labels | `/api/labels` | CRUD; deleting a label removes it from all emails |
| Rules | `/api/rules` | CRUD; stored only (rule engine comes later) |
| Relations | `/api/relation-types`, `/api/relations?collectionId=&recordId=` | types are OWNER-only; relations are validated against the type's collections, both records must exist |
| Proposals | `/api/proposals`, `/api/proposals/count`, `POST /api/proposals/{id}/decision` | APPROVE (optionally with an edited payload) or REJECT; applying stores a `result` (e.g. the created record) and proposals can depend on others (a relation needs its two records): they can only be approved once their dependencies are applied (else 409), rejecting a dependency rejects its pending dependents (audited as `PROPOSAL_CASCADE_REJECTED`), and an AUTO proposal waiting for its dependencies is applied automatically once they are; CREATE_RECORD, UPDATE_RECORD, CREATE_RELATION, CREATE_COLLECTION (OWNER) and DRAFT_REPLY (keeps a reply draft, never sends) can be applied. `?group=FINDINGS` = extracted records (shown on `/findings`), `?group=ACTIONS` = everything the AI proposes to do, incl. calendar entries and to-dos (shown on `/proposals`). Every proposal has an `importance` (LOW/NORMAL/HIGH/URGENT), an `importanceScore` (0–100) and an `importanceReason`, scored by rules when it is created (`ImportanceScorer`: the kind, a deadline or date in the payload, the reply triage's urgency, whether the sender is one of your contacts, someone you wrote to, or a bulk sender); `?minImportance=HIGH` filters (list and count), `?sort=IMPORTANCE` puts the highest score first. Pending proposals from before importance existed are scored once at startup |
| Saved queries | `/api/saved-queries` | CRUD; stored only (execution comes later) |
| Mail | `/api/mail/accounts[/*]`, `/api/emails`, `/api/emails/{id}` | Microsoft Graph sync: connect, callback (always forwards the browser to `/settings/accounts`, with `?connected=true` or `?mailError=`), sync, disconnect, status; disconnected accounts can be reconnected from the Accounts page; mailbox profile photo (`/api/mail/accounts/{id}/photo`, fetched on the first sync after connecting); emails scoped to current user, inbox filterable per account; search `?q=` in `field=ALL|SUBJECT|PEOPLE|CONTENT` (people = sender and to/cc, content = body and preview); `GET /api/emails/suggestions?q=` autocompletes the inbox search with matching senders and their companies (sender domains, free-mail left out); the record Emails tab looks like the inbox (same email boxes, reading pane and pager at the end of the list, 50 per page) and filters the loaded page the same way client-side; remote images in HTML mail stay blocked by the CSP (privacy) and are hidden instead of shown broken, links open in a new tab; in dark mode HTML mail is shown inverted with pictures in their own colors (*Original colors* shows it as sent, on its own light page); `GET /api/emails/{id}/thread` returns the whole conversation of an email in its mailbox (Outlook's conversation id), your own replies from Sent Items included, oldest first; list entries carry `threadSize` and `answered` (a newer sent message exists), and `?answered=false` lists only mail you did not answer yet; the inbox is structured like Outlook: the message list (a compact search field whose scope All/People/Subject/Content is a small menu inside it, and a *Filter* menu for mailbox and *Not answered*) shows email boxes under Today / Yesterday / This week / Last week / month headings with an unread bar and the pager at the end of the list, and the reading pane next to it shows the subject in a fixed box and each message of the conversation in its own box below, scrolling under the subject, and the inbox reading pane and the record Emails tab show that conversation (earlier messages collapsed, your replies marked *You*, quoted history of plain-text mail folded), rows show a message count and an *Answered* mark (and *Reply suggested* / *Draft ready*, `replyState`); the thread also carries the kept reply drafts, the pending reply suggestion and the on-demand state; **Sent Items are synced too** (`folder = sent`, own delta link, same `Mail.Read` scope): they are context for replies and "already answered" checks, shown on the **Sent** side of the *Inbox | Sent* switch above the message list (`GET /api/emails?folder=SENT`; rows show *To: …* with the first recipient's picture; summaries carry `toRecipients`; `answered` does not apply and the *Not answered* filter is hidden there; the search box suggests the people you wrote to, `GET /api/emails/suggestions?folder=SENT`), and never analyzed by the AI or embedded for semantic search. `/inbox?email=<id>` opens that email in the reading pane (used by the links in the proposal and to-do sidebars). Accounts connected before this sync their sent mail on the next sync. **Fast reading:** email lists select only the summary columns (never the bodies; `EmailSummaryQueries.findSummaries`); the inbox prefetches a conversation when the pointer or focus rests on a row (120 ms hover intent) and the neighbours of the email you open, shows the row's sender, subject and preview in the reading pane at once while the conversation loads, and keeps the current page on screen while the next page or filter result loads |
| Senders | `/api/senders/logos[/{domain}]`, `/api/senders/contact-photos[/{id}]` | Sender pictures: Outlook contact photos (read by the mail sync, daily; needs `Contacts.Read`) and sender domain logos (fetched by the backend from `https://<domain>` — public hosts only, raster images ≤ 256 KB — cached 30 days, misses 7 days) |

The full contract with request/response shapes is `api-spec/openapi.yaml` (Swagger UI in the dev profile).

## Code generation (contract first)

`api-spec/openapi.yaml` is the only place the API is defined. Generated code is never edited by hand and never committed.

| Side | Tool | Output | Command |
|---|---|---|---|
| Backend | openapi-generator-maven-plugin (`spring`, interface only) | `contor-api/target/generated-sources/openapi` (`com.example.contor.api`, `.api.model`) | runs on every Maven build, e.g. `./mvnw compile` |
| Frontend | orval (TanStack Query hooks + fetch) | `contor-web/src/lib/api/generated/` | `npm run generate:api` (also runs before `dev`, `build`, `test`, `typecheck`) |

IntelliJ: if `com.example.contor.api` shows as unresolved, run *Maven → Generate Sources and Update Folders* (or `./mvnw generate-sources`) and reload the Maven project; `build-helper-maven-plugin` registers `target/generated-sources/openapi/src/main/java` as a source root.

To change the API: edit the spec, regenerate both sides, then implement. See `docs/decisions/0001-frontend-api-client-orval.md` for the generator choice.

## Tests

```bash
# Backend: unit, slice and integration tests (Docker must be running)
cd contor-api && ./mvnw test

# Backend: extraction quality against the real local Ollama (not part of the default run; see "AI evaluation")
cd contor-api && ./mvnw verify -Pai-eval

# Business mail dataset: rebuild and validate the generated mailbox, test the generator (see "Business mail dataset")
node scripts/business-dataset/build.mjs && node --test scripts/business-dataset/test/

# Frontend: unit/component tests, lint, type check
cd contor-web && npm test && npm run lint && npm run typecheck

# Frontend: end-to-end against the real backend (Docker required). Starts contor-api on port 18080 with a fresh
# Testcontainers Postgres, runs first-run setup through the UI, then the auth and shell flows on port 3100.
cd contor-web && npx playwright install chromium && npm run test:e2e

# Same against a production build (strict CSP without dev allowances). Builds into contor-web/.next-e2e,
# so your own build in .next (and its CONTOR_API_URL) is never replaced.
cd contor-web && npm run test:e2e:prod

# Screenshots of the main screens in light and dark mode -> contor-web/e2e/.tmp/screenshots/ (for design work)
cd contor-web && npm run screenshots
```

## Environment variables

All variables are listed in `.env.example`.

| Variable | Used by | Default | Purpose |
|---|---|---|---|
| `DB_NAME` | compose | `contor` | database name |
| `DB_USERNAME` | compose, backend | `contor` | database user |
| `DB_PASSWORD` | compose, backend | – (required) | database password |
| `DB_PORT` | compose | `5432` | host port, bound to 127.0.0.1 |
| `DB_URL` | backend | `jdbc:postgresql://127.0.0.1:5432/contor` | JDBC URL |
| `APP_BIND_ADDRESS` | backend | `127.0.0.1` | interface Spring binds to |
| `APP_PORT` | backend | `8080` | backend port |
| `APP_ENCRYPTION_KEY` | backend | – (required) | base64 256-bit AES key for `*_enc` columns (`openssl rand -base64 32`) |
| `APP_ENCRYPTION_KEY_VERSION` | backend | `1` | version prefix of encrypted values; change only when rotating keys |
| `APP_SECURE_COOKIES` | backend | `false` | add `Secure` to session and CSRF cookies; set `true` when served over HTTPS |
| `APP_SESSION_IDLE_TIMEOUT` | backend | `30m` | session idle timeout |
| `APP_SESSION_ABSOLUTE_TIMEOUT` | backend | `12h` | maximum session lifetime regardless of activity |
| `OLLAMA_BASE_URL` | backend | `http://127.0.0.1:11434` | local Ollama; must be localhost/127.0.0.1/::1 (the backend refuses to start otherwise) |
| `OLLAMA_TIMEOUT` | backend | `5m` | read timeout of one model call |
| `OLLAMA_NUM_CTX` | backend | `16384` | context window in tokens (more needs more VRAM; changing it reloads the model) |
| `OLLAMA_KEEP_ALIVE` | backend | `30m` | how long Ollama keeps the model loaded after a call |
| `AI_MODEL_ROUTING` | backend | `qwen3.5:9b` | model for category discovery |
| `AI_MODEL_EXTRACTION` | backend | `qwen3.5:9b` | model that reads each email and drafts records (same model = no swapping on 12 GB) |
| `AI_MODEL_EMBEDDING` | backend | `nomic-embed-text` | embedding model, 768 dimensions |
| `AI_TIMEZONE` | backend | `Europe/Berlin` | time zone local times in emails are read in ("Friday 9:00") |
| `AI_PROCESSING_ENABLED` | backend | `true` | analyze new mail in the background after the AI setup |
| `AI_PROCESSING_INTERVAL` | backend | `2m` | pause between background passes |
| `AI_PROCESSING_BATCH_SIZE` | backend | `20` | emails per user and pass |
| `AI_INSIGHTS_INTERVAL`, `AI_INSIGHTS_INITIAL_DELAY` | backend | `24h`, `15m` | how often insights are looked for, and the first time after start |
| `AI_WORK_DAYS` | backend | `MONDAY,…,FRIDAY` | days a reply may offer meeting times on |
| `AI_WORK_START`, `AI_WORK_END` | backend | `09:00`, `18:00` | working hours for offered times (local time in `AI_TIMEZONE`) |
| `AI_SLOT_MINUTES`, `AI_SLOT_SEARCH_DAYS` | backend | `60`, `10` | length of an offered slot; how many days ahead when the email names no dates |
| `AI_CONTEXT_ENABLED` | backend | `true` | read each email together with your earlier mail about it (ADR 0009) |
| `AI_CONTEXT_BUDGET_CHARS` | backend | `6000` | upper bound of that context in the prompt |
| `AI_CONTEXT_SENDER_EMAILS`, `AI_CONTEXT_RELATED_EMAILS` | backend | `5`, `5` | earlier emails from the same sender / related emails (embeddings) in the context |
| `AI_CONTEXT_RELATED_MAX_DISTANCE`, `AI_CONTEXT_RELATED_WITHIN` | backend | `0.25`, `180d` | how close (cosine distance) and how recent a related email must be |
| `MSGRAPH_CLIENT_ID` | backend | – | Azure app registration client ID |
| `MSGRAPH_CLIENT_SECRET` | backend | – | Azure app registration client secret |
| `MSGRAPH_TENANT_ID` | backend | `common` | Entra ID tenant ID (`common` for work/school + personal) |
| `DEV_ACCOUNT_USERNAME`, `DEV_ACCOUNT_PASSWORD`, `DEV_ACCOUNT_EMAIL` | backend (dev profile) | empty | Login the dev backend creates/resets on start (see *Dev account*) |
| `DEMO_MAILBOX_ENABLED` | backend (dev profile) | `false` | import the fictional business mailbox into the dev login once (see *Demo mailbox*) |
| `DEMO_MAILBOX_PATH` | backend (dev profile) | `src/test/resources/ai-eval/business/mailbox` | folder of the generated messages, relative to `contor-api` |
| `DEMO_MAILBOX_SHIFT_TO_TODAY` | backend (dev profile) | `false` | move the demo mail by whole weeks so the newest mail is recent |
| `APP_WEB_URL` | backend | `http://localhost:3000` | Web app URL the browser is forwarded to after the Microsoft OAuth callback (`/settings/accounts?connected=true`) |
| `MSGRAPH_REDIRECT_URI` | backend | `http://localhost:8080/api/mail/accounts/callback` | OAuth2 callback URL matching app registration |
| `MSGRAPH_SCOPES` | backend | `Mail.Read,User.Read,Contacts.Read,offline_access` | Microsoft Graph API permissions, comma- or space-separated; keep `offline_access` (refresh token); `Contacts.Read` gives sender photos from your Outlook contacts |
| `MAIL_SYNC_INTERVAL` | backend | `5m` | Background email delta synchronization interval |
| `CONTOR_API_URL` | frontend (server only) | `http://127.0.0.1:8080` | backend URL: target of the `/api/*` rewrite (fixed at build time) and of server-side calls |
| `CONTOR_HTTPS` | frontend | `false` | `true` adds `Strict-Transport-Security` (only when served over HTTPS) |

## Frontend structure and redesigns

The UI is built so a visual redesign does not touch pages or data logic:

| Layer | Where | Change it for… |
|---|---|---|
| Design tokens (colors, radius, fonts; light + dark) | `contor-web/src/styles/tokens.css` | palette, typography, roundness |
| Primitives (shadcn/ui, owned) | `src/components/ui/` | how a button/input/dialog looks in general |
| App components | `src/components/app/` (`AppButton`, `FormField`, `PageHeader`, `EmptyState`, `ErrorMessage`, `AuthCard`, forms) | app-wide patterns |
| Layout | `src/components/layout/` (`AppShell`, `AppSidebar`, `TopBar`, `CommandPalette`, `UserMenu`) | shell and navigation |
| Pages | `src/app/` | composition only: no styling decisions, no `components/ui` imports (ESLint enforces) |

Navigation entries live in `src/lib/navigation.ts` (shared by sidebar and command palette). `npm run screenshots` captures before/after images.

The sidebar can be resized (drag its right edge, or focus the edge and use the arrow keys; double-click collapses it) and collections can be reordered (drag them, or focus a collection's grip and press Alt+↑/↓). Both are per-browser preferences in `localStorage` (`contor.sidebar.width`, `contor.sidebar.collectionOrder`), never stored on the server. The primary color is blue (buttons, tabs, progress, focus rings, current page); otherwise color comes from `success`/`info`/`warning`, ten tints (`--tint-1..10`: same lightness and chroma, only the hue changes; collections by id, Calendar and To-dos in their section colors, records in their own color so a collection's banners differ) and their vivid variants for the sidebar icons, all in `src/styles/tokens.css`. The command palette hint shows ⌘K on macOS/iOS and Ctrl K elsewhere. Scrollbars are thin everywhere (`globals.css`).

## Notes

- Only loopback is trusted as a proxy for `X-Forwarded-For` (the Next.js server). Do not put another proxy in front without adjusting `server.tomcat.remoteip.internal-proxies`.

- The backend JVM always runs in UTC (all timestamps are `timestamptz`). This also avoids PostgreSQL 18 rejecting legacy JVM zone names such as `US/Pacific`.
- Ollama is not part of compose; it runs natively for GPU access and must stay on localhost (enforced by the backend).
