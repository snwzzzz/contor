# 0007 – Local AI with Ollama: model, client and permissions

**Status:** accepted (Phase 2)

## Context
The assistant must read the user's mail, find out what the inbox is about (people, organizations, job applications,
orders, …), keep its own calendar and to-do list, and propose or apply changes. Mail must never leave the machine.
The target machine has an RTX 3060 (12 GB VRAM) and 16 GB RAM; Ollama runs natively on the host.

## Decision
1. **Model: `qwen3.5:9b` for routing and extraction** (6.6 GB, q4_K_M), `nomic-embed-text` for embeddings.
   One model for both roles stays resident in VRAM; two LLMs (e.g. a 4b router and a 14b extractor) would not fit
   together on 12 GB and Ollama would swap them per email. qwen3.5 follows JSON schemas reliably and handles German and
   English mail. `gemma4:12b-it-qat` is the configurable alternative (`AI_MODEL_EXTRACTION`). Measured: ~12 s per email
   (warm, 16k context), first call after a cold start ~20–50 s.
2. **Plain `RestClient` instead of Spring AI.** contor already had small `LlmClient`/`EmbeddingClient` interfaces and
   only needs `/api/chat`, `/api/embed`, `/api/version`, `/api/tags` and `/api/pull`. `OllamaClient` (~200 lines) sets
   `format` (JSON schema → grammar-constrained output), `think: false`, `temperature 0`, `num_ctx` and `keep_alive`
   directly, is testable with `MockRestServiceServer`, and avoids a large dependency that tracks Spring Boot 4 closely.
   A chat answered with 5xx is retried once (the first call after a cold start can hit a model that is still loading).
3. **Loopback only.** `AiProperties` rejects any Ollama base URL that is not `localhost`/`127.0.0.1`/`::1`, so the
   application refuses to start with a remote model server.
4. **Coarse permissions in `ai_settings`**, set in a setup wizard: a mail-access master switch, the history window,
   and OFF / SUGGEST / AUTO for categories (new collections), records, calendar and to-dos. SUGGEST stores a PENDING
   proposal; AUTO applies it right away (`AUTO_APPLIED`, audited as the AI) and falls back to a pending proposal when
   applying fails. `autonomy_setting` stays for later per-collection tuning.
5. **Calendar and to-dos are system collections** (`events`, `tasks`) seeded like People/Organizations, so they reuse
   records, proposals, record sources, relations and filters instead of new tables.
6. **Analysis runs** (`ai_analysis_run`) on a single background thread: discovery (map-reduce over sender/subject/
   snippet → collection suggestions, validated in Java), an optional review pause, then extraction email by email.
   Model calls run outside transactions; every database step is its own short transaction, so progress is visible and
   a cancel takes effect before the next email. New mail of onboarded users is processed on a schedule.

## Consequences
- Every value the model returns is validated by the field type handlers; invalid values are dropped, never stored.
- Processing an inbox of 1–2k emails takes hours on this GPU; the UI shows progress and the run can be stopped.
- Changing the model is configuration only; `processing_version` on emails allows reprocessing after prompt changes.
