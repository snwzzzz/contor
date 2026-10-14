# AI implementation plan (Phase 2: local Ollama assistant)

> Living plan. Each step is done test-first (CLAUDE.md §0) and updates the README in the same change.
> **Status:** steps 1–9 implemented (see ADR 0007). "Later" items are open.

## Goal

A local model (Ollama, never leaves the machine) that

1. reads the connected inbox once ("what has happened so far"), **discovers the kinds of things in it** (people,
   organizations, jobs/applications, orders, deliveries, invoices, subscriptions, trips, …) and creates matching
   collections,
2. fills those collections with records extracted from each email (deduplicated via match keys),
3. keeps its **own calendar** (system collection *Events*) and **own to-do list** (system collection *Tasks*),
4. keeps processing new mail after the initial run,
5. does nothing until the owner granted permissions in a **step-by-step setup wizard**.

## Model selection

Hardware: RTX 3060 **12 GB VRAM**, 16 GB RAM, Ollama 0.40.

| Role | Model | Size (q4_K_M) | Why |
|---|---|---|---|
| Discovery + extraction | **`qwen3.5:9b`** | 6.6 GB | Best JSON-schema adherence in the size class, strong German/English, thinking can be switched off (`think: false`) for fast structured output. Leaves ~5 GB for a 16k context and the embedding model. |
| Embeddings | `nomic-embed-text` | 0.3 GB | Already fixed by the schema (`vector(768)`). |
| Alternative | `gemma4:12b-it-qat` | 7.2 GB | Configurable via `AI_MODEL_EXTRACTION`; benchmark on your own mail if qwen misses fields. |

Rejected: `qwen3:14b` (9.3 GB, would leave too little room for context and forces model swaps), 26b+ models (do not fit
in VRAM, spill to RAM, ~5× slower), separate small routing model (a second resident model on 12 GB means Ollama swaps
models per email; one model doing both is faster overall).

Measured on this machine: ~12 s per email (warm), ~20–50 s for the first call after a cold start. The initial analysis
of 90 days (~1–2k emails) therefore takes several hours in the background; the UI shows progress and it can be stopped.

## Integration choice: plain `RestClient`, not Spring AI

The linked guide uses Spring AI's `ChatClient`. contor already has its own small `LlmClient`/`EmbeddingClient`
abstraction, runs on Spring Boot 4.1, and only needs three Ollama calls (`/api/chat` with a JSON-schema `format`,
`/api/embed`, `/api/tags` + `/api/pull`). A ~150-line `OllamaClient` over `RestClient` avoids a large, fast-moving
dependency, is fully testable with `MockRestServiceServer`, and gives direct control over `format`, `think`,
`num_ctx` and `keep_alive`. (ADR 0007.)

## Permissions model

Stored per user in `ai_settings` (one row per user, created on first read):

| Permission | Values | Meaning |
|---|---|---|
| `mailAccess` | on/off | AI may read and analyze the user's mailboxes (master switch) |
| `historyDays` | 30 / 90 / 365 / null (all) | How far back the initial analysis reads |
| `categories` | OFF / SUGGEST / AUTO | Create new collections (CREATE_COLLECTION) |
| `records` | OFF / SUGGEST / AUTO | People, organizations and discovered collections |
| `calendar` | OFF / SUGGEST / AUTO | Events collection |
| `tasks` | OFF / SUGGEST / AUTO | Tasks collection |

SUGGEST → `proposal` (PENDING) reviewed on /proposals. AUTO → applied immediately (`AUTO_APPLIED`); if applying fails the
proposal is kept as PENDING so nothing is lost. `autonomy_setting` stays for later fine-grained tuning.

## Steps

### Step 1 – Ollama client and model status (backend)
- `AiProperties`: defaults `qwen3.5:9b`; `ollama.timeout`, `ollama.num-ctx`, `ollama.keep-alive`; **reject non-loopback
  base URLs at startup** (security rule: Ollama stays on localhost).
- `ai/OllamaClient implements LlmClient, EmbeddingClient`: `/api/chat` (`stream:false`, `think:false`,
  `format=<schema>`, `temperature 0`), `/api/embed`, `/api/version`, `/api/tags`, `/api/pull`. Replaces `NoopLlmClient`.
- `service/AiModelService`: status of Ollama + each configured model; `pullMissingModels()` on a single background
  executor with in-memory progress.
- API: `GET /api/ai/status`, `POST /api/ai/models/pull` (OWNER).

### Step 2 – AI settings / consent (backend)
- `V1_0_16__ai.sql`: `ai_settings`, `ai_analysis_run`.
- `AiSettings` entity, `AiPermissionLevel` enum, `AiSettingsService` (get-or-create, update, audit `AI_SETTINGS_CHANGED`).
- API: `GET/PUT /api/ai/settings` (PUT OWNER).

### Step 3 – Own calendar and to-dos (backend)
- Seed system collections **Events** (`starts_at`, `ends_at`, `all_day`, `location`, `notes`; calendar view) and
  **Tasks** (`state` STATUS open → in_progress → done, `due_date`, `priority` ENUM, `notes`; kanban view) and the
  relation type `attends` (People → Events).

### Step 4 – Discovery: "what has happened so far" (backend)
- `InboxDiscoveryService`: map-reduce over sender/subject/snippet of the window (batches of 40) → candidate types →
  one merge call → validated `NewCollectionCommand`s (allowed field types only, existing slugs excluded, ≤ 8 types,
  ≤ 10 fields each, match keys + title field set).
- `CollectionSchemaService.createAiCollection` (created_by = AI) and `CreateCollectionApplier` (OWNER-only).

### Step 5 – Extraction (backend)
- `EmailExtractionService implements ExtractionService`: per email one schema-constrained call built from the
  collections the AI may write to (fields + descriptions + allowed enum/status values) → `ProposalDraft`s.
  Values are filtered against the collection schema; match-key lookups turn duplicates into UPDATE_RECORD (only
  filling empty fields) or drop them.
- `ProposalService.submitDraft`: store as PENDING or apply as AUTO_APPLIED (fallback to PENDING on failure).

### Step 6 – Analysis runs (backend)
- `AiAnalysisService` (start / latest / continue / cancel) + `AiAnalysisWorker` on the AI executor:
  `QUEUED → DISCOVERING → (AWAITING_REVIEW) → EXTRACTING → COMPLETED` (or `FAILED`/`CANCELLED`), progress counters,
  emails get `processing_state` DONE/FAILED/SKIPPED and `processing_version`.
- Scheduled processing of new mail for users who completed onboarding (`app.ai.processing.interval`).
- API: `POST /api/ai/analysis`, `GET /api/ai/analysis/latest`, `POST /api/ai/analysis/{id}/continue|cancel`.

### Step 7 – Setup wizard (frontend)
Modal over any page (`?ai-setup=1`), steps:
1. **Welcome** – what the assistant does, everything stays local.
2. **Model** – Ollama reachable? models installed? one-click download with progress.
3. **Permissions** – mail access + history window, then OFF / Ask me / Automatic for categories, contacts & records,
   calendar, to-dos.
4. **Analyze** – saves the settings, starts the run, live progress.
5. **Review** – (only when categories = Ask me) approve/reject the discovered categories, then continue extraction.

Plus: AI settings section (status, models, permissions, re-run), Autonomy section (same permission editor), live
`ProcessingIndicator` in the top bar.

### Step 8 – Calendar, to-dos and proposals UI (frontend)
- `/calendar` month view of Events, `/tasks` grouped list with check-off, `/proposals` review list
  (approve/reject, auto-applied history).

### Step 9 – Docs
- README (models, `ollama pull`, env vars, flow), architecture.md, ADR 0007.

## Later (not in this phase)
- Relations from extraction (person *works_at* organization, *attends* event) once records get stable ids in a batch.
- Embeddings + semantic search over mail; reply drafts; label/rule proposals; per-collection autonomy using
  `autonomy_setting`; undo for AUTO_WITH_UNDO.
