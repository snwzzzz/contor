# 0008 – Email embeddings: storage, text and backfill

**Status:** accepted (Phase 3, C1)

## Context
Semantic search (C2) and, later, retrieval for extraction (C3) need a vector per email. `email_embedding` already exists
(`vector(768)`, composite key email + model, HNSW cosine index) so the embedding model can change. The local model is
`nomic-embed-text` (768 dimensions), reached through the existing `EmbeddingClient`.

## Decision
1. **Storage through a JPA entity.** `EmailEmbedding` maps the vector with `hibernate-vector` (`float[]`, `@JdbcTypeCode(SqlTypes.VECTOR)`)
   and a composite `@EmbeddedId (emailId, model)`; it is a plain child table like `email_label`, written one row at a time.
   jOOQ stays reserved for the generated collection tables (ADR 0004). Similarity queries (C2) are native SQL in the repository
   (`embedding <=> cast(:query as vector)`), because JPQL has no operator for it.
2. **What is embedded:** `search_document: <subject>\n<sender name or address>\n<cleaned body>`. `EmailTextCleaner` drops quoted lines,
   reply headers with the history below them, forwarded-message headers, the signature after `-- ` and mobile footers, then cuts at a
   word boundary to 4000 characters (about 1000 tokens, inside Ollama's default 2048-token embedding context). Queries use the
   `search_query:` prefix (C2).
3. **Inbox only.** Sent mail is context for replies, not a search target, so it gets no vector; every search is scoped to the
   owner's mailboxes.
4. **When:** the analysis worker embeds an email right after its extraction (a failing embedding model never fails the email), and a
   backfill on the single AI executor catches up on existing mail, mail that failed before and mail after a model change. It runs
   shortly after start and then at the processing interval; progress (`indexed` / `total` / `running`) is part of `GET /api/ai/status`.
5. **Model change:** vectors are keyed by model, so the new model fills in next to the old one. The old model's rows are deleted only
   after the backfill for every user completed, so search keeps working during the switch.
6. **Failure handling:** a model that is not installed stops the backfill (retried at the next interval); an email whose vector has the
   wrong size is skipped; a batch that stores nothing ends the run, so a permanently failing email cannot loop.

## Consequences
- Embedding ~1000 mails takes minutes on the 3060 and queues behind analysis work on the single AI thread.
- Embeddings of mail bodies are derived data: they stay in the local database, are covered by the same backups, and are deleted with the email.
- Changing `AI_MODEL_EMBEDDING` to a model with another dimension needs a migration (`vector(768)` is fixed), the service rejects such vectors.
