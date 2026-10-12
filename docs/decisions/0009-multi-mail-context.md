# 0009 – Multi-mail context for the local model

## Status

Accepted (AI v2, step 4).

## Context

The extraction read one email at a time. Many emails only make sense together with earlier ones: "Could we move the
interview to Monday? Same place." does not say which interview or which place; a payment reminder may not repeat the
invoice number or amount; "as discussed" points at a call or an earlier email. Read alone, such emails produce wrong
values ("location: Same place"), duplicates under new titles, or nothing at all.

The model (`qwen3.5:9b`, 16k context) is answer-bound: reading 1 200 prompt tokens takes under a second, writing a
200-token answer about seven (measured in step 3). Extra context is therefore cheap; asking for more output is not.

## Decision

Before an email is extracted, `MailContextService` collects, from the same owner's mail and **only mail received before
the email** (an older email must not be read with knowledge of later ones during the initial analysis):

1. the earlier messages of its conversation, the user's own replies included;
2. up to 5 earlier emails from the same sender outside the conversation;
3. up to 5 related emails: nearest neighbours of the email's own embedding (pgvector `<=>`, HNSW index) within 180 days
   and a cosine distance of 0.25 — the worker now embeds an email *before* extracting it;
4. "collection: title" of the records already created from any of these emails (`record_source`).

`MailContextPrompt` renders this between the collection list and the email, within a character budget (6 000 by
default). When it does not fit, related emails go first, then the sender history, then the oldest conversation
messages; the record lines always stay. The prompt says to record only what the email states or changes, to use the
earlier emails to understand it, to return an existing record with exactly the same title (so deduplication turns it
into an update) and still to record new to-dos and appointments.

Retrieval instead of long context: putting whole mailboxes into the prompt would not fit 16k tokens, would mix unrelated
mail into every answer and would make the cost grow with the mailbox. The relations call does not get the context.

All of it is configurable (`app.ai.context.*`, `AI_CONTEXT_*`) and can be switched off.

## Consequences

- Measured with `AiContextEvalTest` (three cases in `ai-eval/context`, each run with and without context): with
  context 2 of 4 expected records and 6 of 6 fields correct (the moved interview keeps its title and location, the
  reminder yields the full invoice R-2026-077 with 680 EUR); without it 1 of 4 and 1 of 2 — the reminder produced
  nothing. The to-dos of these cases are missed either way (a weakness of the extraction itself).
- The main eval corpus has no context, so its prompts are unchanged.
- Each email costs a few more database queries and ~1 s more prompt reading at most; failures of the context lookup
  are logged and the email is read on its own.
- Emails whose extraction fails are now embedded anyway (embedding comes first).
