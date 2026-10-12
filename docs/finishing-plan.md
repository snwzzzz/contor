# Finishing implementation plan (cards, detail sidebars, sent mail)

> Living plan. Each step is done test-first (CLAUDE.md §0), follows the React best-practices skill, and updates the
> README in the same change.
> **Status:** steps 1–4 implemented (2b and recipient suggestions included; findings use the same sidebar); only 4d (later) is open.
>
> Where the implementation differs from the text below:
> - The proposal sidebar takes the proposal from the list (refetched after each decision) instead of `useGetProposal`.
> - Record updates are `PUT` and validation errors arrive as one message (`ErrorResponse`), so a failed save shows that message
>   in the sidebar, not per field.
> - Sent-mail suggestions: `GET /api/emails/suggestions?folder=SENT`, `EmailMessageRepository.findRecipients` (native
>   `jsonb_to_recordset` over `to_recipients`); the service keeps one code path for people/companies.
> - Approving with edits shows decision errors through the global toast (the list's mutation), not inside the sheet.
> - `/inbox?email=<id>` preselects an email (used by the sidebars' "Open in inbox").

## Goal

1. **Same height for cards:** the proposal cards in one row line up (top and bottom), buttons always at the bottom.
2. **Proposal detail sidebar:** click a proposal card → a sidebar with everything the assistant proposes, why, and the
   email it came from; decide from there.
3. **To-do detail sidebar (editable):** click a to-do card → a sidebar with all its details, editable (title, state,
   deadline, priority, notes), plus where it came from.
4. **Sent mail:** see your own sent emails in the mail view (list, search, thread), not only as context inside threads.

## What exists already

| Piece | State |
|---|---|
| Proposal grid | `ProposalList.tsx`: auto-fill grid with `items-start` (cards only as tall as their content, changed in `e56daa6`); `ActionCard` + `ReplySuggestionCard` |
| Proposal data | `GET /api/proposals/{id}`, `ProposalDto` has `payload`, `reasoning`, `confidence`, `model`, `promptVersion`, `sourceEmailId`, `createdAt`; decision endpoint accepts an edited `payload` (`EDITED_AND_APPROVED`) |
| Source email | `GET /api/emails/{id}` and `/thread` (owner-scoped, sent ids allowed); `EmailReader` / `EmailThread` render it |
| Sheet primitive | `components/ui/sheet.tsx` (shadcn, Radix dialog: focus trap, Esc, overlay) — only used by `sidebar.tsx` so far |
| To-do board | `TaskList.tsx` on `KanbanBoard`; cards expand inline (read-only details + Start / Done / Reopen + "Open details" link); `useUpdateRecord` already used for moves |
| To-do fields | system collection `tasks`: `state` (STATUS open/in_progress/done), `due_date` (DATE), `priority` (ENUM low/normal/high), `notes` (LONG_TEXT); record writes validated by `RecordService` (`RecordValidationException` → 400 with all violations) |
| Record edit UI | none yet (`RecordDetail` is read-only) |
| Sent mail sync | `folder = sent`, own delta link, `processing_state = SKIPPED`; included in threads and the `answered` flag; **excluded** from `listEmails` (`EmailSpecifications.matching` hard-codes `folder <> sent`), suggestions and embeddings |
| Summary DTO | `EmailSummaryDto` has `fromName/fromAddress` but **no recipients** (a sent row needs "To: …") |

## Decisions (proposed defaults — confirm or change)

1. **Equal height per row, not one global height.** CSS grid rows stretch their items; long content is clamped
   (title 2 lines, reasoning 3, reply body 5, preview 4 values) so rows stay close in height. The full content is in the
   new sidebar, which is what makes clamping acceptable. (Alternative: a fixed card height for all rows.)
2. **One shared sidebar composite.** `components/app/DetailSheet.tsx` wraps `ui/sheet` (right side, `sm:max-w-lg`,
   header with icon/label/title, scrolling body, sticky footer for actions). Used by proposals and to-dos (and later
   records/calendar). Pages keep composing only `components/app`.
3. **Sidebar state is local** (`selectedId` in the list component, like the inbox's selected email), not in the URL.
   A deep link `?proposal=` / `?task=` can be added later without changing the components.
4. **To-do editing: explicit Save**, not autosave. The form keeps a draft; Save sends only changed fields
   (`UpdateRecordCommand`, `null` = unchanged); Cancel/close with changes asks "Discard changes?". Server violations
   are shown next to their fields.
5. **The to-do card stops expanding inline.** Clicking the card body opens the sidebar; the checkbox (done) and
   dragging stay on the card. The inline Start/Done buttons move into the sidebar.
6. **Sent mail is a folder switch in the mail view**, `Inbox | Sent` above the list (segmented control), not a
   separate page. Sent mail stays out of AI processing and semantic search (keyword search only) in this plan.
7. **Proposal editing in the sidebar (record proposals)** is step 2b and optional: the read-only sidebar ships first.

---

## 1. Same height for cards (frontend only, S)

- `ProposalList.tsx`: grid back to default `items-stretch` (remove `items-start`); update the comment.
- `ActionCard`: `h-full`, body `flex-1`, footer stays last (`mt-auto` via the flex column) → buttons align across a row.
- `ReplySuggestionCard`: same (`h-full`, body `flex-1`); only when `full` is false (the thread view keeps natural height).
- Clamp check: title `line-clamp-2`, reasoning `line-clamp-3`, reply body `line-clamp-5`, preview `PREVIEW_VALUES = 4`
  (exists). Add a "Show details" affordance (step 2) so nothing is hidden for good.
- Kanban to-do cards stay content-height (a board column is a list, equal heights would only add blank space). Say so
  in the README UI notes.
- Tests (Vitest): cards carry `h-full`/the grid has no `items-start` (class assertion, as done for other layout
  guards); visual check in the running app (`/run`) with one long and one short card in a row.

## 2. Proposal detail sidebar (frontend only, M)

### 2a. Read-only sidebar with decision
- `components/app/DetailSheet.tsx` (new, see decision 2) + test: renders title, closes on Esc and the close button,
  footer slot.
- `components/app/proposals/ProposalDetail.tsx` (new), props `proposalId`, `onClose`:
  - `useGetProposal(proposalId)` (fresh copy; the list item is the placeholder via `initialData` from the list cache
    so it opens instantly).
  - **Header:** icon + label from `describe()` (move `describe`, `Description`, `GROUPS` to
    `lib/proposal-describe.ts` so list and sidebar share it), title, confidence bar, created date, model + prompt version
    in small muted text.
  - **What it would do**, per kind:
    - `CREATE_RECORD`: all values (not only 4), formatted with `formatValue`, target collection name + link.
    - `UPDATE_RECORD`: a before/after table — `useGetRecord(collectionId, recordId)` for the current values, changed
      rows highlighted (tokens only, e.g. `bg-primary/5`), unchanged fields hidden behind "Show all fields". Link to the
      record.
    - `CREATE_COLLECTION`: name, description, field list (name, type, description).
    - `SET_LABEL` / `CREATE_LABEL` / `ARCHIVE_EMAIL` / `CREATE_RELATION`: the payload as a key/value list (generic
      fallback, same as today's preview).
    - `DRAFT_REPLY`: To/Cc/Subject + full body (`ReplyBody`), Edit & keep opens the existing `ReplyDraftDialog`.
  - **Why:** full `reasoning`.
  - **Source email:** when `sourceEmailId` is set, `useGetEmail(sourceEmailId)` → a compact block (sender picture,
    sender, date, subject, snippet) with "Show email" expanding the full `EmailReader` inline and "Open in inbox"
    (link to `/inbox?email=<id>`; the inbox reads the param once to preselect — small `InboxView` change + test).
  - **Footer:** Reject / Approve (same mutation and invalidation as the card; the sheet closes on success).
- `ProposalList.tsx`: `selectedId` state; the card body becomes a button (`aria-label="Show details for …"`) that
  opens the sidebar; the card's own Approve/Reject keep working without opening it. When the selected proposal
  disappears from the list (decided elsewhere), close the sheet.
- Tests (Vitest, `mockApi`): opens on card click; all values shown for a create; before/after for an update with the
  changed row marked; source email block and "Show email"; approve from the sidebar sends the decision and closes;
  Esc closes; 404 on the proposal → error message in the sheet. Playwright: proposals flow opens and approves from
  the sidebar.

### 2b. Edit before approving (optional)
- For `CREATE_RECORD` / `UPDATE_RECORD`: "Edit" switches the value list to inputs (reusing `RecordFieldInput` from
  step 3) and Approve sends `{ decision: "APPROVE", payload: { ...payload, values: edited } }` → the backend already
  stores `EDITED_AND_APPROVED` with `finalPayload`. Applier validation errors (400) show in the sheet.
- Tests: edited approval sends the edited payload; validation error stays in the sheet.

## 3. To-do detail sidebar, editable (frontend only, M)

- `components/app/collections/RecordFieldInput.tsx` (new, reusable for records later): one input per field type,
  driven by the collection's `CollectionFieldDto`: `TEXT` → `Input`, `LONG_TEXT` → `Textarea`, `DATE` → date input
  (ISO `YYYY-MM-DD`, empty = `null`), `ENUM`/`STATUS` → select of `options.values` / `options.states` with humanized
  labels; other types render read-only (`formatValue`) until needed. Labelled via `FormField`, shows a field error.
  Unit test per type (value in/out, empty → `null`).
- `components/app/tasks/TaskDetail.tsx` (new), props `collection`, `task`, `onClose`:
  - **Header:** checkbox + title input (big, borderless until focus), state badge.
  - **Fields:** State, Deadline (with the relative label from `describeDue` beside it), Priority, Notes — rendered
    through `RecordFieldInput` from `collection.fields` (archived fields skipped), so new task fields appear without
    code changes.
  - **Quick actions:** Start / Back to to do / Mark done / Reopen (same `move` as the board; moves to the top of the
    column).
  - **Origin:** "Added by the assistant/you on …"; source emails via `useListRecordEmails(collectionId, recordId)`
    (record sources are part of it) as compact rows linking to the inbox; link "Open as page" to the record route.
  - **Footer:** Delete (confirm dialog, `useDeleteRecord`), Cancel, Save (disabled until dirty; loading state).
  - Save: `useUpdateRecord` with only changed `title`/`values`; success → refresh the board query, toast "Saved",
    keep the sheet open; `RecordValidationException` (400) → map violations to fields (`meta: { silent: true }` so the
    global toast does not double up).
  - Unsaved changes + close/Esc/overlay click → "Discard changes?" confirm.
- `TaskList.tsx`: `selectedId` state; `TaskCard` loses `expanded`/the inline region; the card body is a button that
  opens the sidebar (`aria-haspopup="dialog"`); the checkbox and drag stay. The sidebar reads the task from the board
  data (so an optimistic move is reflected) and closes when the task disappears (deleted elsewhere).
  Make sure a drag does not fire the click (drag start sets a ref, click ignores it once).
- Tests (Vitest): click opens with all values; edit title + priority → PATCH body has only those two; date cleared →
  `due_date: null`; server 400 with a violation → message under the field and no toast; discard prompt on close with
  changes; Mark done moves the card; delete asks and removes. Update `TaskList.test.tsx` (no inline expand any more).
  Playwright: open a to-do, change the deadline, save, the card shows the new deadline after reload.

## 4. Sent mail in the mail view (backend + frontend, M)

### 4a. API (spec first, then regenerate)
- New query parameter `folder` on `GET /api/emails`: schema `EmailFolder` (enum `INBOX`, `SENT`; default `INBOX`).
  No domain enum exists (folders are `EmailMessage.FOLDER_*` strings), so no name clash; map `SENT → FOLDER_SENT`.
- `EmailSummaryDto` gets `toRecipients: RecipientDto[]` (the stored jsonb list; needed for "To: …" on sent rows and
  the recipient picture). Mapper fills it for all rows.
- `GET /api/emails/suggestions` gets the same optional `folder`; for `SENT` it suggests **recipients** (people you
  wrote to) instead of senders.
- `answered` is ignored for `SENT` (document it in the spec); `processingState` filters too.

### 4b. Backend
- `EmailSpecifications.Criteria` gets `folder` (`null` = inbox, keeps every existing caller unchanged); the hard-coded
  `folder <> sent` becomes `folder = sent` or `(folder is null or folder <> sent)`. Record emails and the embedding
  query stay inbox-only.
- `MailService.listEmails(..., folder, ...)` passes it on; sort stays `receivedAt desc` (for sent mail the sync stores
  the sent time there — verify in `MailSyncService` around line 215 and add a test if not covered).
- `EmailMessageRepository.findRecipients(ownerId, pattern, page)` for sent suggestions: native query over
  `jsonb_array_elements(to_recipients)`, grouped by address with counts, owner-scoped, escaped `LIKE` (same helper as
  senders).
- Thread stats (`threadSize`) already count both folders; `answered`/`replyState` are `false`/`null` for sent rows.
- Tests: `EmailSpecificationsTest` (inbox excludes sent, sent excludes inbox, owner scope kept), repository test for
  `findRecipients` (counts, owner scope, escaping), `MailMapperTest` (`toRecipients`), `MailServiceTest`
  (`listEmails_SentFolder`, `suggestEmailSenders_SentFolderSuggestsRecipients`), `MailControllerTest`
  (`folder=SENT` passed through, invalid value 400, 401).

### 4c. UI
- `InboxView`: `folder` state (`INBOX`/`SENT`) with a segmented control `Inbox | Sent` above the search bar (an app
  composite `mail/FolderSwitch.tsx`, radio-group semantics); switching resets page, selection and the "Not answered"
  filter (hidden for Sent). Passes `folder` to `useListEmails` and the search suggestions.
- `EmailRow`: for sent mail show "To: Anna, Bob +2" (helper `recipientsLabel` in `mail-format.ts`, unit-tested) and
  the picture of the first recipient (`useSenderPictures` gets the recipient domains); no unread bar, no answered or
  reply marker.
- Reading pane: `EmailThread` already loads a thread from a sent id; hide "Suggest a reply" when the selected email is
  a sent one; the selected (sent) message is the expanded one.
- Empty state for Sent: "No sent mail synced yet" (sync runs with the inbox).
- Tests (Vitest): switching to Sent requests `folder=SENT`, hides the Not-answered filter, rows show "To: …";
  opening a sent email shows the thread without "Suggest a reply"; `recipientsLabel` cases. Playwright: Sent tab lists
  a sent seed email (extend the e2e seed if needed).
- README: Mail section (Sent folder, `folder` parameter, suggestions) and UI description.

### 4d. Later (not in this plan)
- Sent mail in semantic search (embed sent mail; the embedding query excludes it today).
- Sent mail on the record Emails tab (mail *to* the record's addresses).

---

## Order and size

| Step | Content | Size |
|---|---|---|
| 1 | equal-height proposal cards | S |
| 2a | `DetailSheet`, proposal sidebar (read-only + decide, source email, update diff) | M |
| 3 | `RecordFieldInput`, editable to-do sidebar, card click instead of inline expand | M |
| 4a–4b | `folder` parameter, `toRecipients`, recipient suggestions | S–M |
| 4c | Inbox/Sent switch, sent rows, thread tweaks | M |
| 2b | edit record proposals before approving (optional) | S |

Step 1 first (smallest, independent). 2a before 3 because it introduces `DetailSheet`; 3 introduces
`RecordFieldInput`, which 2b reuses. Step 4 is independent of 1–3 and can run in parallel.

Each step ends green (`./mvnw test`, `npm test`, `npm run lint`, `npm run typecheck`, `npm run test:e2e` for the
touched flows) with the README updated.
