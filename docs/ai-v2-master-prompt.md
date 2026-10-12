# Master prompt — AI v2

Paste the block below into a fresh Claude Code session in this repository. Replace `<STEP>` with the step to work on
(e.g. `Step 2`). One step per session keeps context small; the plan file carries the state between sessions.

---

```
You are implementing one step of contor's AI v2 plan.

Read first, in this order, and follow them exactly:
1. CLAUDE.md (working rules, Java style, data/frontend/security rules — they override everything else).
2. docs/ai-v2-plan.md (the plan; implement only <STEP>, respect its "Decisions" and "Security and privacy").
3. docs/ai-plan.md and docs/replies-plan.md (what exists: extraction, analysis worker, reply pipeline).
4. The code the step touches. Start from: AiAnalysisWorker, EmailExtractionService, ReplySuggestionService,
   ReplyTriageService, ReplyComposerService, ProposalService, MailService, EmailEmbeddingService, OllamaClient,
   api-spec/openapi.yaml, and contor-web/src/components/app/{mail,proposals,replies}.
5. For frontend work: the vercel-react-best-practices skill and contor-web/node_modules/next/dist/docs/.

Goal of AI v2 (context only — do not build other steps):
- reply drafts that answer the questions in an email, with free slots from the user's calendar for scheduling
- optional courtesy drafts (job rejection → thank you, decline an offer, thank-you notes)
- non-obvious "insight" proposals found across many emails, each with evidence and follow-up actions
- an importance level on every proposal, filterable and sortable
- the model understands an email in the context of its thread, the sender's history, related emails and linked records
- faster inbox reads and faster AI processing, proven with measurements

How to work:
- Before writing code, restate <STEP> as a short checklist and list the files you will touch. If the plan leaves a
  decision open or conflicts with the code or CLAUDE.md, ask me before deciding.
- Tests first for every piece: write the test, run it, confirm it fails for the right reason, then implement, then
  refactor. Backend tests against real Postgres via Testcontainers. Use the test styles of CLAUDE.md per layer.
- API changes: edit api-spec/openapi.yaml first, regenerate (Maven build / orval), never edit generated code.
- Migrations: next free V1_0_N number, one concern per file, named constraints, comment on new tables/columns.
- Prompts: bump the prompt version constant when a prompt changes; keep the stable part of the prompt first and the
  email last; validate every model answer (schema, ids belong to the user, lengths) and drop what fails.
- Model output is untrusted: email ids, record ids and slugs from the model are checked against the user's data.
- Privacy: never log prompts, bodies, drafts, questions or full addresses. Every new query is owner-scoped and has a
  cross-user isolation test.
- Performance work: measure before and after with the step 1 timings; put the numbers in the commit message.
- AI quality: run the relevant ai-eval tests; the extraction eval must not get worse. Add the eval cases the step names.
- Keep changes inside the step. Note follow-ups in the plan's step instead of implementing them.

Definition of done for <STEP>:
- ./mvnw test (contor-api), npm test, npm run lint, npm run typecheck (contor-web) all green;
  npm run test:e2e when a flow in the UI changed.
- README.md updated (new env vars, settings, endpoints, behaviour); CLAUDE.md updated if a rule or enum pair is new.
- docs/ai-v2-plan.md: the step's status updated, plus an "Implementation notes" bullet wherever the code differs from
  the plan.
- A summary for me: what changed, test results, measured numbers (if any), open questions.
Do not commit or push unless I ask.
```
