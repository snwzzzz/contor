# Replies implementation plan (threads + AI reply drafts)

> Living plan. Each step is done test-first (CLAUDE.md §0) and updates the README in the same change.
> **Status:** Part A (threads) implemented — thread endpoint, `threadSize`/`answered`, `?answered=` filter and the
> thread view in the inbox and the record Emails tab. Part B (AI reply drafts) and C open; nothing is ever sent.

## Goal

1. **Threads (Part A):** see a conversation as a whole — the email, your replies (Sent Items) and their answers — through
   the API and in the UI (inbox reading pane and the record Emails tab). In the list, see which emails you already
   answered and how long a conversation is.
2. **AI reply drafts (Part B):** for inbox emails that need an answer from you, the local model writes a reply draft in
   the thread's language, grounded in the thread and your records. It is a **proposal**: you read, edit, keep or dismiss
   it. It is **never sent** and nothing is written to Outlook in this phase.

## What exists already

| Piece | State |
|---|---|
| Sent Items sync | `folder = sent`, own delta link (`V1_0_19`), `processing_state = SKIPPED`, never listed or analyzed |
| Conversation id | `email_message.conversation_id` from Graph for inbox **and** sent mail, indexed (`ix_email_message_conversation_id`) |
| `internet_message_id` | stored; `headers` column exists but is **never filled** (delta does not return `internetMessageHeaders`) |
| `DRAFT_REPLY` | in `ProposalKind`, the API enum and `ck_proposal_kind`; no producer, no applier; UI label "Draft reply" + "Replies" chip |
| Edited approval | `POST /api/proposals/{id}/decision` with an edited payload → `EDITED_AND_APPROVED`, `finalPayload` kept |
| Body cleaning | `EmailTextCleaner` strips quoted history, reply headers, signatures (used for extraction and embeddings) |
| OAuth scope | `Mail.Read,User.Read,Contacts.Read,offline_access` — no write or send rights (stays so in A and B) |

## Decisions (proposed defaults — confirm or change)

1. **Inbox stays a flat list** with a thread badge ("3") and an "answered" mark; the *reading pane* shows the whole
   thread. A grouped "one row per conversation" inbox can come later without API changes beyond a `group` flag.
2. **Thread = same `conversation_id` in the same mailbox** (Graph's own grouping). No header-based (`In-Reply-To`)
   threading: headers are not synced, and Outlook's conversation id already follows replies and forwards.
3. **Approving a reply draft does not send.** It stores the (edited) text as a *ready* draft shown in the thread with
   **Copy** and **Open in mail app** (`mailto:` with to/cc/subject/body). Writing into Outlook's Drafts folder
   (`Mail.ReadWrite`) and sending (`Mail.Send`) are Part C.
4. **New permission `replies`: OFF / Ask me** (no *Automatic*: an "automatic" draft would have nothing to do without
   sending). Default OFF; the wizard and Settings → Autonomy get a row "Reply drafts".
5. **At most one pending draft per conversation**; it is superseded by a newer inbound email (a new draft replaces it)
   and dismissed automatically when you reply yourself (a newer sent email in the conversation shows up in the sync).

---

## Part A — Threads through the API and in the UI

### A1. Persistence and queries (backend)
- `V1_0_21__email_threads.sql`: composite index `ix_email_message_account_conversation (account_id, conversation_id,
  received_at)`; comment why (thread lookup + per-page thread stats). No new table.
- `EmailMessageRepository`:
  - `findThread(ownerId, accountId, conversationId)`: all messages of the conversation in that mailbox, **sent included**,
    oldest first, owner-scoped (`EmailSpecifications.Criteria.ownerId` stays mandatory; add an `includeSent` option
    instead of the hard-coded `folder <> sent` filter, default `false`).
  - `threadStats(accountIds/conversationIds of a page)`: one grouped query → per conversation `messageCount`,
    `lastSentAt` (newest sent message), `lastReceivedAt`. No N+1 for the list.
- Tests (`@RepositoryTest`): thread is owner- and mailbox-scoped, includes sent, ordered; stats count both folders;
  emails without `conversation_id` are a thread of one.

### A2. API (spec first, then regenerate)
- `EmailSummaryDto` gets `threadSize` (int, ≥ 1) and `answered` (boolean: a sent message in the conversation is newer than
  this email). `EmailDto` gets the same plus `folder` already exists.
- New `GET /api/emails/{emailId}/thread` → `EmailThreadDto { conversationId?, subject, participants: RecipientDto[],
  messages: EmailDto[] (oldest first; sent ones have folder = "sent") }`. 404 when the email is not the user's.
- `MailService.getThread(emailId)`, `MailMapper.toThreadDto`; `listEmails`/`listRecordEmails` fill the new summary
  fields from `threadStats`.
- Record Emails tab: `listRecordEmails` keeps listing inbox mail only; the thread endpoint shows replies when one is
  opened. (Optionally later: include sent mail *to* the record's addresses.)
- Tests: mapper (thread DTO, participants de-duplicated), service (`getThread_NotOwner_NotFound`,
  `listEmails_ThreadStats`), controller (200/404/401), spec-driven `answered` cases.

### A3. UI
- `mail/EmailThread.tsx` (new, used by the inbox and the record Emails tab instead of a single `EmailReader`):
  - fetches `useGetEmailThread(emailId)`; the selected email and the newest message are expanded, earlier ones collapsed
    to one line (sender, date, snippet) and expand on click; "Expand all".
  - your own messages (folder `sent`) are labelled **You** with your account avatar and a subtle tint (tokens only).
  - each expanded message reuses `EmailReader` (body with formatted/plain switch); plain-text bodies fold the quoted
    history behind "Show quoted text" (client-side, same rules as `EmailTextCleaner`: lines starting with `>`,
    "On … wrote:" / "Am … schrieb:" headers).
- `EmailRow`: thread badge (`threadSize > 1`) and a reply icon with "Answered" tooltip when `answered`.
- Inbox filter chip "Not answered", server-side (`listEmails?answered=false`: `NOT EXISTS` a newer sent message in the
  conversation) — a client-side filter would be wrong across pages.
- Tests (Vitest): thread renders oldest→newest, collapsed/expanded states, "You" for sent, badge + answered icon on rows,
  `answered=false` filter request; Playwright: open an email with a reply → both visible.
- README: Mail section (thread endpoint, answered flag, filter), UI description.

---

## Part B — AI proposes replies (never sends)

### B1. Permission
- `V1_0_22__ai_replies.sql`: `ai_settings.replies text not null default 'OFF'` + `CHECK (replies in ('OFF','SUGGEST'))`.
- `AiSettings.replies`, `AiSettingsDto`/`UpdateAiSettingsCommand.replies` (spec), audit unchanged
  (`AI_SETTINGS_CHANGED`). Wizard permissions step + Settings → Autonomy row "Reply drafts: Off / Ask me" with the
  sentence "Drafts are never sent; you copy or open them in your mail app."
- Tests: settings service/controller round-trip, validation rejects `AUTO`.

### B2. Which emails get a draft (cheap rules first, then one small model call)
`ReplyCandidateFilter` (pure, unit-tested) — skip when any holds:
- sender is you / one of your mailboxes, or a no-reply style address (`no-?reply`, `do-?not-?reply`, `mailer-daemon`,
  `notifications?@`, `bounce`), or a free text marker of automation in the cleaned body ("This is an automated message");
- you are not in **To** (only Cc/Bcc) — newsletters and mass mail usually fail this;
- the conversation already has a sent message newer than this email (`answered`);
- older than the processing window (initial analysis: only the last 14 days get drafts, even when history is longer —
  nobody wants 300 drafts for last year's mail);
- a pending `DRAFT_REPLY` for a newer email of the same conversation exists.

Then `ReplyTriage` (one schema-constrained call, `think:false`, tiny output):
`{ "needsReply": bool, "reason": "…", "urgency": "low|normal|high" }` with the cleaned email and the last two thread
messages. Only `needsReply = true` continues.

(Headers such as `List-Unsubscribe`/`Auto-Submitted` would make the rules sharper; Graph's delta does not return them.
Optional follow-up: fetch `internetMessageHeaders` with a per-message `GET … ?$select=internetMessageHeaders` only for
candidates — still covered by `Mail.Read`.)

### B3. Writing the draft
`ReplyDraftService` (`prompt version reply-v1`, separate from extraction so the extraction eval stays stable):
- **Context** (truncated to fit `num_ctx`): the thread oldest→newest, cleaned with `EmailTextCleaner`, your own sent
  messages marked as *you* (they teach tone and facts you already gave); your display name and mailbox; today's date,
  weekday and `AI_TIMEZONE`; small grounding from records: the sender's People/Organization record (name, role,
  company) and your upcoming Events/Tasks that mention the sender or thread subject (max 5).
- **Rules in the prompt:** answer in the language of the last inbound message; match its formality (Sie/du); never
  invent facts, dates, prices or commitments — where you must decide, write a placeholder in square brackets
  (`[available times]`, `[price]`) and list it in `placeholders`; no signature beyond your first name; keep it short.
- **Output schema:** `{ "subject": "Re: …", "body": "plain text", "language": "de|en|…", "placeholders": ["…"],
  "summary": "one line what the reply does" }`. Validation: non-empty body, ≤ 4 000 chars, subject defaults to
  `Re: <subject>` when missing, placeholders must occur in the body.
- Tests: prompt building (language, you-marking, truncation), parsing/validation, placeholder check (`MockRestServiceServer`
  or a fake `LlmClient`, like `EmailExtractionService` tests).

### B4. Proposal, applier and lifecycle
- **Payload** (documented in the spec's `ProposalAction` description):
  `{ emailId, accountId, conversationId, to: [{name,address}], cc: [...], subject, body, language, placeholders: [] }`.
  `to` = the inbound sender (reply-to if present later), `cc` = the inbound Cc minus your own addresses.
  `source_email_id` = the email answered; group **ACTIONS** (shows under "Replies").
- `DraftReplyApplier` (`@Component`): validates the final payload (to non-empty, body non-empty, email still owned by the
  deciding user) and stores it in **`reply_draft`** (`V1_0_23__reply_drafts.sql`: `id`, `email_id` FK, `account_id` FK,
  `conversation_id`, `to_recipients jsonb`, `cc_recipients jsonb`, `subject`, `body`, `status` (`READY`, `DISCARDED`;
  later `IN_OUTLOOK`, `SENT`) with CHECK, `proposal_id` FK, timestamps). Result `{ replyDraftId }`. Not owner-only
  (a draft changes nothing outside contor). Never logs the body.
- **Lifecycle hooks:**
  - after each sync, pending `DRAFT_REPLY` proposals whose conversation got a newer **sent** email → `REJECTED` with
    reason "answered in Outlook" (audited like a cascade, `PROPOSAL_SUPERSEDED`);
  - a newer **inbound** email in the conversation supersedes the old pending draft; the new email may get its own.
- **Where it runs:** in `AiAnalysisWorker` after extraction of an inbox email (same executor, same progress), only when
  `replies = SUGGEST`. A failed draft step never fails the email (its extraction result stays); it is logged and not
  retried automatically — the thread view offers **Suggest a reply** to run it on demand for that email.
- API: `GET /api/emails/{emailId}/thread` also returns `replyDrafts: ReplyDraftDto[]` and `pendingReplyProposalId`;
  `PATCH /api/reply-drafts/{id}` (edit text), `POST /api/reply-drafts/{id}/discard`.
- Tests: applier (valid, missing body, foreign email → not found), lifecycle (sent reply dismisses pending, new inbound
  supersedes), worker integration with fake LLM (`replies=OFF` → no calls), controller/security (owner scoping).

### B5. UI
- **Proposals page:** the "Draft reply" card shows To, Subject and the first lines of the body, placeholders highlighted
  as chips; **Edit & keep** opens `ReplyDraftDialog` (to/cc chips, subject, body textarea with placeholders highlighted;
  "Keep" is disabled while `[…]` placeholders are left, with "Keep anyway"); Dismiss as usual.
- **Thread view:** a pending draft appears at the bottom of the thread as "The assistant suggests a reply" with
  Edit & keep / Dismiss; a kept draft appears as **Draft — not sent** with **Copy** and **Open in mail app**
  (`mailto:` — body length-limited; when too long, Copy only) and Discard.
- **Inbox row:** small "Draft ready" / "Reply suggested" marker next to the answered icon.
- Tests: card, dialog (placeholder guard, edited approval sends `payload`), thread draft states, mailto building
  (encoding, length fallback).

### B6. Evaluation (optional but recommended)
- Extend `ai-eval` with `needsReply` labels on existing cases and a few reply cases; score triage precision/recall
  (drafts themselves are judged by reading them — no automatic text score). Private cases stay git-ignored.

### B7. Security and privacy
- Everything stays on the local Ollama; prompts are built from already stored mail; no new scopes.
- Never log drafts, bodies or full addresses (log-masking helper); audit decisions as for other proposals.
- `mailto:` is built client-side from the user's own draft; links never include data in the app URL.

---

## Part C — later (not in this plan)
- **Outlook drafts:** `Mail.ReadWrite` scope + `POST /me/messages/{id}/createReply` with the kept text → status `IN_OUTLOOK`
  (re-consent screen for existing accounts).
- **Sending:** `Mail.Send`, explicit per-message confirmation in the UI, audit `MAIL_SENT`, never automatic.
- Grouped inbox (one row per conversation), reply drafts for forwarded threads, style learning from many sent mails.

## Order and size

| Step | Content | Size |
|---|---|---|
| A1–A2 | thread queries, stats, endpoint, summary flags | M |
| A3 | thread view, answered/badge, "Not answered" filter | M |
| B1 | `replies` permission (DB, API, wizard, settings) | S |
| B2 | candidate rules + triage call | M |
| B3 | draft generation + validation | M |
| B4 | applier, `reply_draft`, lifecycle, endpoints | M–L |
| B5 | proposal card, dialog, thread draft states | M |
| B6 | eval extension | S |

Each step ends green (`./mvnw test`, `npm test`, `npm run lint`, `npm run typecheck`) with the README updated.
