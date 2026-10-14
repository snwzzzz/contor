# Business mail dataset — prompts

Companion to `business-dataset-plan.md`. Section 1 is the **master prompt** for a Claude Code session that builds the
dataset and its evaluations step by step. Sections 2–6 are the **generator prompts** the session uses for each phase;
they are versioned (`W1`, `S1`, …) and their version goes into `manifest.json` with every output.

Placeholders are written `{{like_this}}`. Everything generated is fictional; the prompts enforce it.

---

## 1. Master prompt (paste into a fresh session; replace `<PHASE>`)

```
You are building contor's synthetic business mail dataset and its evaluations.

Read first, in this order, and follow them exactly:
1. CLAUDE.md (working rules; tests first for code; README stays current).
2. docs/business-dataset-plan.md (the plan; do only <PHASE>; respect "Decisions", the storyline catalogue, the file
   format, the labelling guidelines and the acceptance criteria).
3. docs/business-dataset-prompts.md (the generator prompts W, S, R, L, N; use them verbatim, fill the placeholders).
4. The eval code you build on: contor-api/src/test/java/com/example/contor/ai/eval/ (EvalCase, EvalCaseLoader,
   EvalCollections, EvalScorer, BaselineComparison, AiExtractionEvalTest, AiReplyDraftEvalTest, AiInsightEvalTest,
   AiContextEvalTest) and the existing cases in contor-api/src/test/resources/ai-eval/.

Hard rules for every generated file:
- Fictional only: domains end in .example; company and person names invented and not on the deny list in the plan;
  phone numbers +<country> 5550 xxxx pattern; no IBANs, card numbers, real addresses of real companies, or public
  figures. If a name you invent turns out to be a real brand, rename it.
- Labels first: storylines carry facts and labels; rendering may not add facts. Never change a label to fit a text —
  re-render the text instead.
- Use the ledger in world.json for every number (orders, POs, invoices, tracking, prices, dates). Never invent a
  number that is not in the ledger or in the storyline's facts.
- Validate after every batch (scripts/validators described in the plan) and fix before continuing.
- Record in manifest.json: phase, prompt version, model, seed, inputs, counts, validator results.

How to work:
- Restate <PHASE> as a checklist with the files you will create or change. Ask me before deciding anything the plan
  leaves open (company/industry, generator, size).
- Generation phases: work in batches (≤ 5 storylines, ≤ 40 messages); after each batch run the validators, show me
  the counts and 2 sample messages, then continue unless I say stop.
- Code phases (eval integration): tests first (write, see them fail for the right reason, implement, refactor);
  `./mvnw test`, `npm test`, `npm run lint`, `npm run typecheck` green; eval tests run with `./mvnw verify -Pai-eval`.
- Update docs/business-dataset-plan.md (status + "Implementation notes" where you deviate) and the README.
- Summarize at the end: what was produced, validator results, open problems. Do not commit unless I ask.
```

---

## 2. Prompt W1 — world bible

```
You design the fictional world for a synthetic email dataset. Answer with JSON only, matching the schema below.

The company: {{company_name}}, {{company_description}}, located in {{city}}, {{country}}. Mailbox owner:
{{owner_name}}, {{owner_role}}, address {{owner_address}}; shared mailbox {{shared_address}}.
Period: {{period_start}} to {{period_end}} (time zone {{company_zone}}).

Create exactly:
- {{n_internal}} internal people (roles: warehouse lead, accounting, sales DACH, sales export, managing director, …).
- {{n_customers}} customer companies with segments: {{segments_with_counts}}; countries {{customer_countries}}
  (include at least two outside the EU customs union, e.g. CH and NO).
- {{n_suppliers}} suppliers in {{supplier_countries}}; one of them raises prices during the period; one is far
  enough away that road freight takes 4–6 days with a border crossing.
- {{n_carriers}} carriers/logistics providers (road freight, groupage, parcel, pallet network, long-haul trucking, a
  forwarder). Exactly two of them use the same subcontracted driver: same full name, same mobile number; give his name
  a diacritic so a typo variant without it is plausible.
- {{n_services}} service providers (bank – notifications only, transport insurer, ERP vendor, trade fair organiser,
  accountant, facility services).
- {{n_noise}} noise senders (newsletters, webinars, marketplace alerts, system notifications, one phishing imitation
  of a carrier).
- Additionally one person who shares the driver's first name but works for a customer (a buyer).

For every company: id (kebab-case), name, type, country, language(s), time zone, domain (*.example only), segment
(customers), relationships (customerOf/supplierOf/subcontractorOf), payment terms, and 1–4 people.
For every person: id, full name (culturally fitting the country), role, email (at the company domain, the driver also
has a private address at a *.example mail provider), phone (+<country code> 5550 xxxx), language, formality (Sie/du,
first name or not), style (verbose/terse/friendly/curt), typo rate (0–0.05), signature block (multi-line).
Ledger (numbers the storylines may use): customer numbers (K-xxxxx), price list excerpts (10–15 articles with article
number, name, unit, price in EUR; CHF/SEK/NOK prices for the non-euro customers), quote numbers (Q-26-0xxx), sales
order numbers (SO-26-0xxx), purchase order numbers (PO-26-1xxx), outgoing invoice numbers (RE-2026-0xxx), each
supplier's own invoice format, each carrier's tracking/CMR format with 10 sample numbers, complaint/RMA numbers
(RMA-26-0xx). Reserve enough numbers for {{n_storylines}} storylines; no number used twice.

Rules: everything fictional; no real brands, products, people, addresses; domains only *.example; no bank details.
Before answering, check each company name against well-known real companies and rename any that exist.

Schema: {"company": {...}, "internal": [...], "companies": [...], "people": [...], "ledger": {...},
"calendar": {"holidays": [...], "tradeFair": {...}, "ownerAbsences": [...]}}
```

---

## 3. Prompt S1 — storylines (per catalogue entry, batches of ≤ 5)

```
You write storylines for a synthetic business mailbox. Answer with JSON only: {"storylines": [ ... ]}.

World (use only these entities and ledger numbers; mark each number you use as taken):
{{world_json}}
Numbers already taken by earlier storylines: {{taken_numbers}}
Catalogue entry: {{catalogue_entry_text}}   (from business-dataset-plan.md, incl. its edge cases)
Write {{count}} storylines for it. Period {{period_start}}–{{period_end}}; weekdays only unless the entry says otherwise;
business hours of the sender's time zone.

A storyline has: id ("{{category}}-<slug>"), category, entities (ids), summary (2 sentences), difficulty (easy/medium/
hard), editorNotes (what makes it hard, which edge case of the entry it covers), and beats in time order. A beat is
one email: {day (YYYY-MM-DD), time (HH:MM in sender zone), direction (inbound|sent), account (alex|orders), from (person
id), to/cc (person ids), conversation (a key shared by the beats of one Outlook thread; a new key when the sender starts
a new thread about the same topic), inReplyTo (beat index or null), facts, labels, style}.

facts: the exact information this mail states — numbers, amounts with currency, dates (as the sender would write them,
plus the resolved ISO value), names, places, requests, questions. Nothing else may appear in the rendered text.
labels (labelling guidelines of the plan apply):
  records: per collection of the business schema (customers, suppliers, sales_orders, purchase_orders, shipments,
           complaints, invoices, people, organizations, events, tasks) the records this mail creates or changes —
           title + only the values this mail states or that its dependsOn mails make resolvable.
  relations, mustNotProduce, needsReply, reply {intent, questions, scheduleFrom/To, timeOfDay, courtesyKind,
  answeredBy (beat index of the user's reply if any)}, importance (LOW/NORMAL/HIGH/URGENT), dependsOn (beat indexes or
  message keys of other storylines), tags.
style: language (de/en/pl/es/it/tr-en), tone (neutral/friendly/impatient/angry/apologetic/formal), length (1–3
sentences / short / medium / long), quoting (none/full/partial), typos (0–3), signature (full/short/none),
greeting/closing conventions of the sender.

Rules:
- Use realistic business rhythm: replies hours to days later, not instantly; reminders after 3–10 days.
- Every edge case listed in the catalogue entry appears in at least one of the storylines you write.
- The user's own sent mails are beats too (direction sent): they answer, confirm, quote, delegate, follow up.
- Dependencies are explicit: if a mail is only understandable with an earlier one, list it in dependsOn.
- Mails that nobody answers are allowed and wanted where the catalogue says so; say so in editorNotes.
- Do not invent entities or numbers. If you need a number, take an untaken one from the ledger and list it.
Return also {"taken": [numbers you used]}.
```

---

## 4. Prompt R1 — rendering one email

```
You write one realistic business email. Output only the email as JSON: {"subject": "...", "body": "..."}.

Sender: {{person_json}} (company {{company_json}})
Recipients: {{to_cc_json}}
Date: {{date_local}} ({{weekday}}), time zone {{zone}}
Language: {{language}}; tone: {{tone}}; length: {{length}}; typos: {{typos}}; signature: {{signature}}
Thread so far (oldest first, for quoting and continuity): {{previous_messages_in_conversation}}
Quoting: {{quoting}}  (Outlook style: "Von:/Gesendet:/An:/Betreff:" headers in German mail clients, "From:/Sent:/To:/
Subject:" in English ones; quoted history below the new text)
Facts this email must state, and nothing beyond them: {{facts_json}}
What makes it interesting (do not mention this): {{editor_notes}}

Rules:
- State every fact exactly once in the new text (numbers, amounts, dates exactly as given in "as written"). Do not
  add any other number that could be an order, invoice, tracking, phone or price number. Do not add new dates.
- Write like this person writes (formality, verbosity, typical phrases of the country and industry); no marketing
  tone in operational mail; real business people are brief.
- Subject: as a person would write it (often short, sometimes "AW:"/"RE:"/"WG:"/"FW:" prefixes in a thread,
  sometimes unrelated to the content).
- Signature: the person's signature block (or the short form / none as requested); the company footer may include a
  register court and VAT id pattern "DE000000000" (fictional), never bank details.
- Never mention that the mail is synthetic.
```

---

## 5. Prompt L1 — independent label audit

```
You label one business email from scratch for an evaluation. Answer with JSON only. You get the email, the earlier
emails it may refer to, and the label schema with its rules — not the intended labels.

Mailbox owner: {{owner_name}} <{{owner_address}}> (shared mailbox {{shared_address}}).
Email: {{message_json}}
Earlier emails it may depend on: {{depends_on_messages}}
Schema and rules: {{business_schema_and_labelling_guidelines}}

Return: {"records": {...}, "relations": [...], "needsReply": bool, "reply": {...}, "importance": "...",
"mustNotProduce": [...], "uncertain": ["what you were unsure about and why"]}.
```

The session then diffs L1's answer against the storyline labels: agreements pass, disagreements are listed per message
with both versions; the fix is either the label (if the storyline was wrong) or a re-render (if the text did not say
what the facts say). Record agreement rates per label type in `manifest.json`.

In practice: `node scripts/business-dataset/audit.mjs prepare <dir> 3` writes the inputs (mails and their dependencies,
no labels) in chunks; each chunk is labelled in a separate context with `scripts/business-dataset/audit-brief.md` as
`{{business_schema_and_labelling_guidelines}}`, answers go to `<dir>/answers-<n>.json`; `audit.mjs compare <dir>`
prints the agreement rates and the disagreements. The summary is kept in `ai-eval/business/audit.json`.

---

## 6. Prompt N1 — noise and automation mails (batches of 20)

```
You write automated or low-value emails a business mailbox receives. Answer with JSON only: {"mails": [...]}, each
{"senderId", "date", "time", "subject", "body", "kind"}.

Senders (from the world): {{noise_senders_json}}. Period {{period_start}}–{{period_end}}.
Kinds and counts for this batch: {{kinds_with_counts}} (newsletter, webinar, marketplace alert, ERP job notice, bank
notice, out-of-office reply to a mail the owner sent ({{sent_message_summaries}}), bounce, read receipt, calendar
auto-reply, parcel scam imitating {{carrier_name}}, social network digest).
Rules: realistic template language and footers ("unsubscribe", "this is an automated message"); no real brands;
links only to *.example; the scam must look plausible but contain tell-tales (urgent fee, odd domain). These mails
carry labels: no records, needsReply false, importance LOW, mustNotProduce: any record.
```

---

## 7. Validators (deterministic, part of phase T/R; specified here so every session builds the same checks)

| Validator | Fails when |
|---|---|
| schema | a file does not match the message/storyline/world schema |
| fictional | a domain not ending in `.example`, a phone not matching the fictional pattern, a name on the deny list, an IBAN/card-like number |
| ledger | a number in facts or body that is not in the ledger or used by two storylines for different things |
| time order | a message dated before its `inReplyTo` or `dependsOn`; a weekend business mail without the storyline allowing it |
| fact check | a labelled value (normalized: amounts, dates incl. `KW`, numbers with/without leading zeros, diacritics) neither in the body nor marked derivable |
| extra facts | the body contains an order/tracking/invoice-shaped number not in its facts |
| language | detected language ≠ requested (allowing mixed for `tr-en`, `pl-en`) |
| coverage | a catalogue edge case without a storyline tagged with it; category counts outside ±15 % |
| end state | a record in `expected/end-state.json` not traceable to a beat, or a beat change not reflected |
