# External APIs implementation plan (connect any HTTP API, the assistant learns from it)

> Living plan. Each step is done test-first (CLAUDE.md §0), goes through the spec first (§0.3) and updates the README
> in the same change. **Status:** X1–X12 implemented (2026-10-29). The decisions below were confirmed on 2026-10-28.
>
> Implementation notes:
> - X1: ADR 0012 (`docs/decisions/0012-external-apis.md`).
> - X2: `security/OutboundRequestGuard.get(uri, headers, timeout, maxBytes)` returns `Response(status, contentType,
>   body, truncated)` for any status and throws `OutboundRequestException` (host only in the message) when a URI or
>   redirect target is refused or the request fails. A 3xx without `Location` is returned as it is. `LogoFetcher` keeps
>   its own rules on top (status 200, not truncated, raster image). X4 added address pinning (below).
> - X3 (where it differs from the data model below): the interval is `poll_interval_minutes` (15–10080), not an
>   `interval` column. Endpoints are unique by `(api_id, name)`, so one path can be called with different queries. A
>   snapshot stores the answer as `body text` (JSON is parsed when needed). No `icon` column yet (X11). Collection
>   foreign keys are plain like everywhere else, because collections are never deleted. `ApiAuthConfig` (header name,
>   query parameter, token URL, scope, username) and `ApiHeader` are validated JSON records. `ApiFieldMapping` has no
>   `transform` yet. `ApiStatus.POLLED` (ACTIVE, FAILING) is what the scheduler picks up.
> - X4: the guard runs on Apache HttpClient 5 through a package-private `Transport` (`ApacheTransport`). Its only DNS
>   lookup is `PublicAddressResolver`, so the check and the connection use the same addresses (`ApacheTransportTest`
>   proves it with a host only the resolver knows). `send(Request)` adds POST (redirects are followed for GET only) and
>   `Response` carries the headers. `ApiAuthConfig` gained `clientId`. The secret is `ApiSecret {value, headers}` as
>   encrypted JSON. `ExternalApiClient.call(api, endpoint)` validates the credentials before sending anything and
>   throws `OutboundRequestException`, `ApiAuthException` (`credentialsRejected`) or `IllegalArgumentException` for X7
>   to map to call outcomes. Timeout (10 s) and size cap (1 MB) are constants until X7 adds `ExternalApiProperties`.
>   There is no WireMock: the client is tested against a mocked guard, the guard against a fake transport, and the
>   transport against a local socket.
> - X5 (where it differs from the API sketch below): the list returns full `ExternalApiDto`s (no separate summary DTO),
>   with `endpointCount`. Endpoints have no GET by id. `POST /api/external-apis/test` takes the whole unsaved setup
>   (`TestExternalApiCommand`) plus an optional `apiId` for its stored secret, and answers 200 with `ok`/`error` for
>   refused or failed calls. API enum names: `ApiAuthType`, `ApiState`, `EndpointMethod`, `EndpointOrigin`. The secret
>   JSON goes through `externalapi/ApiSecretCodec`, also used by the client. Every update resets the health (ACTIVE,
>   failure count 0, next call now). The test call runs inside the service's read transaction (house rule: every
>   public service method is `@Transactional`), so it holds a connection for up to the 10 s timeout. The `/apps` pages
>   and the icon endpoint are X11, the call history endpoint is X7.
> - X6 (where it differs from the frontend sketch below): `AppsMenu` replaced the launcher in `TopBarButtons` and is
>   mounted by `TopBar`; rows are Radix menu items (arrows, Enter) that `router.push` the modal address, not links. The
>   icon is the tinted initial only (fetched icons come with X11). The footer has *Add app…* only; *Manage apps*
>   follows with `/apps` (X11). The modal is `AppDialog` in `AppShell`. Step 3 has manual endpoints and the test call;
>   "Read the docs" comes with X8. A new app keeps its endpoints in the browser until *Add app*, then creates them
>   one by one. Editing saves the app fields with one PATCH of only the changed values and endpoints right away.
>   Because `secretHeaders` replaces all secret headers and their values are never returned, changing one asks for
>   the values of the others again. The Playwright flow (`e2e/apps.spec.ts`) adds, lists, edits and deletes an app
>   without a test call: the stub server needs the guard's loopback seam (see X7).
> - X7 (where it differs from the scheduling rules below): the poll is split into `ExternalApiPollScheduler` (the
>   `@Scheduled` tick, virtual threads, a semaphore for `max-concurrent`, one host's apps in turn) and
>   `ExternalApiPoller` (one app per transaction: `findDue`, `poll`, `pruneHistory`), so the HTTP calls run inside the
>   app's transaction, at most `max-concurrent` connections. The jitter is derived from app id and time (no random
>   source to stub). Missing settings or unreadable credentials (`IllegalArgumentException`/`IllegalStateException`
>   from the client) count like 401/403: NEEDS_SETUP. A run stops at the first NEEDS_SETUP; the worst endpoint
>   outcome decides (NEEDS_SETUP > failure > 429 > OK). `JsonItems` reads at most 1000 items; keyless items are keyed
>   by their content hash. `ExternalApiProperties` has no `min-interval` (the migration's check enforces 15 min) and
>   adds `history-days` and `snapshots-kept`; `max-response-bytes` is `max-response-size` (a `DataSize`). "Call now"
>   (`POST …/calls`, 202 with the app) only makes the app due; the next tick calls it, so it never runs beside a
>   poll. A turned-off app answers 409 (`ExternalApiDisabledException`). Mapping/insight work (the model queue) comes
>   with X9/X10. **No loopback seam in the guard:** a production switch that lets calls reach local addresses is the
>   SSRF hole the guard exists to close, so `ExternalApiPollIntegrationTest` replaces the guard bean with a mock
>   (client, auth, storage and scheduler real) instead of running a stub server, and the Playwright flow stays
>   without a test call.
> - X8 (where it differs from the sketch): `ApiDocsReader` became `externalapi/OpenApiReader` (code, no model) and
>   `service/ApiDocsModelService` (`api-docs-v1`), sharing `externalapi/DiscoveredEndpoint` (path relative to the base
>   URL, `distinct`, at most 30). Discovery is not per saved app: `POST /api/external-apis/discovery` takes the docs
>   link and base URL (plus an optional `apiId` whose endpoints are left out), so the add modal can read the docs
>   before anything is saved; `GET /api/external-apis/discovery/{discoveryId}` polls. Runs live in memory
>   (`ApiDiscoveryStore`, 1 h, 10 per user), so there is no discovery table and no
>   `ExternalApiBusyException`. Suggestions are GET only, also from the model (POST read queries stay something the
>   user adds by hand). Required query parameters come from examples, defaults or the first enum value; an operation
>   whose required parameters have none is skipped. YAML is parsed with SnakeYAML's safe constructor with an alias
>   limit (no "billion laughs"). First eval run (qwen3.5:9b): 5 of 5 expected paths, 8 suggestions, 0 forbidden; the
>   model left every item key empty and once gave `$.results` without `[*]`, worth a prompt change (`api-docs-v2`,
>   measured against two runs of v1) later.
> - X9 (where it differs from the sketch): `record_source.api_item_id` deletes with its item (the app), so provenance
>   goes with a deleted app instead of staying as "deleted app"; `proposal.source_api_item_id` is set null. Mapping
>   proposals are made by `ExternalApiMappingService` (model `ApiMappingModelService`) only for endpoints without any
>   mapping, at most once a day when nothing fits, and only into existing collections (a new-collection proposal is
>   left for later). The payload carries a display `title` ("App · Endpoint") the applier ignores. Ingest
>   (`ExternalApiIngestService`) runs after the poll's transaction (the poller returns the new and changed item ids per
>   endpoint) and submits each proposal in its own transaction, so a failing AUTO falls back to a suggestion without
>   undoing the poll; after an approval it is started by `ExternalApiMappingActivated` (after commit, on a virtual
>   thread). `ProposalService.submitDraftFromApiItem` takes the item. Apps with *Let the assistant learn from it* off
>   get neither mappings nor record proposals. Values are copied as they are; converting them by field type (money,
>   dates) is open. First eval run (qwen3.5:9b): 3 of 3 collections, 4 of 4 expected fields; it mapped a plain number
>   to a MONEY field, which the record validation will reject.
> - X10: `ExternalApiItemRepository.findDigest` (new or gone in the 14-day window, apps with `aiAnalysis`, at most 40) feeds
>   `DigestApiItem` lines (`A<n> day · app · endpoint · new|gone: label json`, 6 000 characters) into
>   `InsightModelService` (`insight-v2`). Measured against two runs of `insight-v1` (3 of 3 expected, 6 insights each):
>   a first v2 with the app rules always in the prompt lost the price-change case in both runs (2 of 4), so the app
>   rules and the `evidenceApiItems` schema field are now added only when the digest has app data; then 4 of 4 in both
>   runs, 8 insights in total (one of them noise: a new posting restated). Unknown `A` ids drop the insight, like
>   unknown emails. Model keys are `model:<emails>|A<items>`. The rule `InsightDetectors.goneItem` needs a gone item with
>   a record that has email sources (no email, no insight); other "item gone that a record refers to" cases are left to
>   the model. The source email of an insight proposal stays its first evidence email (none for app-only insights).
> - X11 (where it differs from the sketch): no icon endpoint and no `icon` column yet: the tinted initial is the app's
>   picture everywhere (fetching a favicon through `LogoFetcher` once is left for later). *Pause* turns the app off
>   (`enabled`), *Resume* on; PAUSED stays the state after 10 failures. Endpoints carry their mapping summary
>   (`EndpointMappingDto`: the active mapping, else the proposed one, else the last switched off) instead of a mappings
>   endpoint; the proposal card links to `/proposals`, not to the single proposal. `ProposalDto.sourceApp` names the app
>   and endpoint of a record proposal; findings and the record page do not show app origins yet (they show no email
>   origin either). The apps are in the command palette ("Apps" group) and in `DATA_NAV`.
> - X12: app items are a chat source next to records and mail: `ChatRetrievalService` searches the plan's searches in
>   the owner's current items without a record (`ILIKE` on key and JSON, `%`/`_` escaped), 3 per search, 10 in all,
>   independent of mail access; `ReportScopeService.collectQuestion` takes them and `EvidencePack` renders them as
>   `D<n>` (A and X were taken by analysis rows and hidden rows). Citations get `CitationType.APP_ITEM` (reports never
>   produce one: their evidence has no app data) with `chat_citation.api_item_id` (set null when the app goes) and the
>   cited data in `quote`. The answer prompt is unchanged (`chat-answer-v2`): it already asks for any code in brackets,
>   and there is no chat eval to measure a prompt change against; the tests cover that `D` codes are read and cited.
> - Fix after X12: X7 had switched polling off in `src/test/resources/config/application.yml`, which the local launcher
>   (`TestContorApplication`, `spring-boot:test-run`) also reads, so apps were never called there. The switch is now
>   only on the two integration tests that run the poll themselves (`@SpringBootTest(properties = ...)`). A due call
>   reads *Due now* on the app page instead of "2 min. ago".
> - Data view after X12: TMDB's answers (`{"page", "results": [...]}`) were stored, but the docs reader had given the
>   endpoints `$[*]`, which finds nothing in an object, so no item was read and nothing said so; fixing the path did not
>   help either, because the same answer then counted as UNCHANGED. Now `JsonItems.fits` checks the path against the
>   answer (a wrong path is said on the call and keeps the items), `updateEndpoint` forgets the stored answers when what
>   decides the answer or how it is read changes, and `GET …/endpoints/{endpointId}/data` + `EndpointData` show the items
>   and the last answer, with the detected list as a one-click fix.

## Goal

The user connects external APIs to contor (a shop's order API, a job platform, a weather or exchange-rate API, a
parcel tracker, …). The local model reads the API's documentation, learns what the endpoints return, and contor calls
them on a schedule. What comes back becomes knowledge like mail does today: records in collections (findings),
relations, and **insights** that combine API data with mail and the calendar ("the job posting you applied to on
Monday was taken down", "order SO-155 shipped, but the customer still asks where it is").

- Add an API in a modal: name, base URL, description, documentation link, auth and headers, how often to call it.
- "Read the docs" lets the model propose endpoints (or an OpenAPI document is parsed directly); the user confirms them.
- "Test call" shows the real answer before anything is saved.
- All connected APIs are listed in the top bar's **Apps** dropdown (`AppLauncher`, today "No apps yet") as a compact
  IntelliJ-style list: icon, name, a status mark, last call.

## What already exists and is reused

| Piece | Where | Use for external APIs |
|---|---|---|
| Apps dropdown placeholder | `components/layout/TopBarButtons.tsx` `AppLauncher` | the list of connected APIs + "Add app" |
| SSRF guard (HTTPS, public addresses only, redirect re-check, size cap) | `service/LogoFetcher` | extracted into a shared `security/OutboundRequestGuard` |
| AES-GCM columns | `persistence/converter/EncryptedStringConverter`, `MailAccount.*_enc` | API keys, tokens, secret header values |
| Provider abstraction + registry | `mailprovider/MailProviderRegistry`, ADR 0011 | auth strategies (`ApiAuthHandler` per auth kind) |
| Scheduled passes, single GPU thread | `@EnableScheduling` (`MailConfig`), `InsightService.runDaily`, `AiExecutors.analysis()` | due-call scheduler; model work queues behind mail |
| HTML → text | `ai/HtmlText` | documentation pages without OpenAPI |
| JSON-schema model answers | `ai/JsonSchemas`, `LlmClient.completeJson` | endpoint discovery, mapping, insights |
| Proposals + appliers | `proposal/*Applier`, `ProposalService` | records/insights from API data are suggestions first |
| Insights | `ai/Insight`, `InsightService`, `InsightApplier` | evidence widened from emails to API snapshots |
| Provenance | `record_source` (record ↔ email) | record ↔ API snapshot |
| Modals addressed by query param | `SETTINGS_PARAM`, `AI_SETUP_PARAM` in `lib/navigation.ts` | `?app=new`, `?app=<id>` |
| Stepper, form fields | `components/app/Stepper`, `FormField` | the add/edit modal |

## Decisions

1. **Name in the UI: "Apps"**, in code `ExternalApi` (table `external_api`). The launcher already says "Apps".
2. **Read-only first.** contor only sends `GET` (and `POST` only for endpoints the user explicitly marks as a read
   query, e.g. GraphQL/search). The model never picks method, host or path at runtime: it can only choose among
   endpoints the user confirmed. Writing to an external API (place an order, apply to a job) is a later step and would
   go through a proposal with `requiresOwner()`.
3. **Private per user**, like mailboxes: `owner_id`, proposals go to that user, other users never see the config.
   Records created from API data are shared data (like records from mail).
4. **Secrets never leave the backend.** Auth kinds: `NONE`, `API_KEY_HEADER`, `API_KEY_QUERY`, `BEARER`, `BASIC`,
   `OAUTH2_CLIENT_CREDENTIALS` (later: OAuth authorization code, reusing the mail OAuth flow). Custom headers are
   key/value pairs, each with a `secret` flag; secret values go into an encrypted JSON column, plain ones into `jsonb`.
   DTOs show only header names and `hasSecret`; an update with `null` keeps the stored secret. Secrets are never logged,
   never part of a prompt, never stored in snapshots (request headers are not kept).
5. **Outbound safety (SSRF).** Every call goes through `OutboundRequestGuard`: HTTPS only, the resolved address must be
   public (checked again after every redirect, at most 3, and the connection is made to the checked address to stop
   DNS rebinding), 10 s timeout, 1 MB response cap, JSON/XML/text/HTML only. Ollama, Postgres and the Spring port stay
   unreachable. *Optional later:* an owner-only `allowPrivateNetwork` flag per app for a home NAS or local service.
6. **API responses are untrusted input**, like email bodies: they go into prompts as quoted data with source tokens,
   and every id, slug, date or enum in the model's answer is validated before it is stored (CLAUDE.md §3).
7. **Learn once, then map deterministically.** The model is used for (a) reading the docs, (b) proposing a *mapping*
   from an endpoint's items to a collection (once, confirmed by the user), (c) insights. Regular polls apply the
   confirmed mapping in code: no model call per item, stable results, cheap on the single GPU thread.
8. **No change, no work.** Each response is hashed; an unchanged answer only updates `last_called_at`. Changed answers
   are diffed by item key (new / changed / gone), and only the diff reaches the mapper and the model.

## Data model (`V1_0_35__external_apis.sql`, one concern; `V1_0_36__external_api_provenance.sql`)

```
external_api                 owner_id → app_user, name, slug, description, base_url, docs_url, icon (bytea, nullable),
                             auth_kind (text + ck), auth_config jsonb (non-secret: header name, query param, token url),
                             secret_enc (encrypted JSON: api key / token / client secret / secret header values),
                             headers jsonb [{name, value}] (plain), poll_interval interval (min 15 min, default 6 h),
                             enabled, ai_analysis (bool), status (ACTIVE | FAILING | PAUSED | NEEDS_SETUP, ck),
                             last_called_at, next_call_at, failure_count, last_error (masked, ≤ 500 chars),
                             daily_request_limit (default 200), uq (owner_id, slug)
external_api_endpoint        api_id → external_api, name, method (GET | POST, ck), path (relative, validated),
                             query jsonb, body jsonb (POST only), items_path (e.g. "$.data.orders[*]"),
                             item_key_path (e.g. "$.id"), description, source (USER | DOCS | MODEL, ck), enabled,
                             uq (api_id, method, path)
external_api_mapping         endpoint_id → endpoint, collection_id → collection, field_map jsonb
                             [{fieldSlug, path, transform?}], title_path, state (PROPOSED | ACTIVE | OFF, ck)
external_api_call            endpoint_id, started_at, duration_ms, http_status, outcome (OK | UNCHANGED | FAILED, ck),
                             error (masked), items_new / items_changed / items_gone   -- run history, pruned to 30 days
external_api_snapshot        endpoint_id, fetched_at, content_hash, content_type, body jsonb or body_text,
                             truncated (bool)                                   -- keep the last 5 per endpoint
external_api_item            endpoint_id, item_key, item_hash, data jsonb, first_seen_at, last_seen_at, gone_at,
                             collection_id + record_id (nullable: the record it maps to), uq (endpoint_id, item_key)
```

Provenance (`V1_0_36`): `record_source` gets a nullable `api_item_id` next to `email_id`, with a check that exactly one
of them is set (`ck_record_source_origin`) and the unique constraint split per origin. INSIGHT payloads get
`evidenceApiItemIds` next to `evidenceEmailIds`. New proposal kind `MAP_API_ENDPOINT` (owner check not needed: the API
is the user's own; schema changes it implies go through `CREATE_COLLECTION`/`ADD_FIELD` proposals as today) in
`ck_proposal_kind`, with a base in `ImportanceScorer`. New audit actions: `EXTERNAL_API_CREATED`, `_UPDATED`,
`_SECRET_CHANGED`, `_DELETED`. `AiSettings` gets `externalApis` (`AiPermissionLevel`, default `SUGGEST`).

## Backend components

| Package | Class | Job |
|---|---|---|
| `domain` | `ExternalApi`, `ExternalApiEndpoint`, `ExternalApiMapping`, `ExternalApiCall`, `ExternalApiSnapshot`, `ExternalApiItem`, enums `ApiAuthKind`, `ApiStatus`, `ApiHttpMethod`, `EndpointSource`, `MappingState`, `CallOutcome`; value objects `ApiPath` (relative, no `..`, no scheme/host), `JsonPathExpr` (restricted subset), `BaseUrl` (https, no userinfo) | entities as in §2 |
| `security` | `OutboundRequestGuard` (from `LogoFetcher`, which then uses it) | SSRF rules, pinned address, size/time caps |
| `externalapi` (plug-in engine) | `ApiAuthHandler` + one `@Component` per `ApiAuthKind`, `ApiAuthRegistry` | applies auth to a request; client-credentials caches its token in memory |
| `externalapi` | `ExternalApiClient` | builds the request from a confirmed endpoint only, guard + auth + headers, returns `ApiResponse(status, contentType, body, truncated)`; honours `Retry-After` |
| `externalapi` | `JsonItems` | extracts items by `items_path`/`item_key_path`, hashes, diffs against `external_api_item` |
| `ai` | `ApiDocsReader` (OpenAPI 3/Swagger 2 parsed in code; otherwise `HtmlText` → model, `api-docs-v1`) | proposes endpoints; only relative paths under `base_url`, max 30 |
| `ai` | `ApiMappingModelService` (`api-mapping-v1`) | sample items + collection catalog → `MAP_API_ENDPOINT` proposal (collection, field map) or a new collection proposal |
| `ai` | `InsightDetectors` / `InsightModelService` extended | API items (new/changed/gone) join the 14-day digest with `A<n>` source tokens |
| `service` | `ExternalApiService` | CRUD, secrets, test call, audit (owner = current user, 404 for others) |
| `service` | `ExternalApiDiscoveryService` | "Read the docs" run (202 + polling, like insights) |
| `service` | `ExternalApiPollService` | `@Scheduled(fixedDelay = 1m)` picks due apps (`enabled and next_call_at <= now`), calls their endpoints on a small virtual-thread executor (max 4 concurrent, max 1 per host), writes `external_api_call`, snapshots, items; queues mapping/model work on `AiExecutors.analysis()` |
| `service` | `ExternalApiIngestService` | applies an `ACTIVE` mapping: new item → `CREATE_RECORD` proposal (or direct create when `externalApis = AUTO`), changed → `UPDATE_RECORD`, gone → nothing (field `gone_at` only); links `record_source` |
| `proposal` | `MapApiEndpointApplier` | activates the mapping; ingests the current items once |
| `mapper`, `controller`, `exception` | `ExternalApiMapper`, `ExternalApiController implements ExternalApisApi`, `ExternalApiNotFoundException`, `ExternalApiEndpointNotFoundException`, `ExternalApiBusyException` (409) | as per the style guide |

**Scheduling rules** (`ExternalApiPollService`): `next_call_at = now + interval ± 10 % jitter`; on failure exponential
backoff (interval × 2^failures, capped at 24 h), status `FAILING` after 3, `PAUSED` after 10 (user resumes); 401/403 →
`NEEDS_SETUP` immediately (bad credentials, no retries); 429 → `Retry-After` or one interval; the daily request limit is
counted from `external_api_call`. "Call now" bypasses the interval but not the guard, the limit or a running call. The
model work is coalesced: at most one mapping/insight job per app in the queue. Disabled when
`app.external-apis.enabled=false` (tests, CI).

**Config** (`ExternalApiProperties`): `app.external-apis.enabled`, `.poll-tick` (1m), `.min-interval` (15m),
`.max-response-bytes` (1 MB), `.timeout` (10s), `.max-concurrent` (4). README + `application.yml` documented.

## API (`api-spec/openapi.yaml`, tag `ExternalApis`)

```
GET    /api/external-apis                       list (the Apps dropdown)            ExternalApiSummaryDto[]
POST   /api/external-apis                       NewExternalApiCommand → 201         ExternalApiDto
GET    /api/external-apis/{id}                  detail incl. endpoints, mappings
PATCH  /api/external-apis/{id}                  UpdateExternalApiCommand (null = keep; secrets write-only)
DELETE /api/external-apis/{id}                  204 (items/snapshots go; records stay, provenance kept as "deleted app")
POST   /api/external-apis/test                  TestExternalApiCommand (unsaved config) → ApiTestResultDto (status,
                                                content type, first 20 KB, detected items)
POST   /api/external-apis/{id}/discovery        202, read the docs → GET …/discovery (state, proposed endpoints)
GET|POST /api/external-apis/{id}/endpoints      list / add (NewExternalApiEndpointCommand)
PATCH|DELETE /api/external-apis/{id}/endpoints/{endpointId}
POST   /api/external-apis/{id}/calls            202 "call now"
GET    /api/external-apis/{id}/calls            last 50 calls (ExternalApiCallDto)
GET    /api/external-apis/{id}/icon             image (fetched once from the base URL's host via LogoFetcher)
```

API enum names must not clash with domain enums (CLAUDE.md §2): `ApiAuthType`→`ApiAuthKind`, `ApiState`→`ApiStatus`,
`EndpointOrigin`→`EndpointSource`, `ApiCallResult`→`CallOutcome`, `ApiMappingStatus`→`MappingState`. The secret is a
`writeOnly` property; responses carry `hasSecret` and `headers[].secret` without values.

## Frontend

- **Apps dropdown** (`AppLauncher` → `components/app/apps/AppsMenu.tsx`, data from `useListExternalApis`): a dense
  list like IntelliJ's project tool window — 16 px icon (fetched icon or first letter on a tint), name, muted host,
  right-aligned relative "last call"; a small status dot only for `FAILING`/`NEEDS_SETUP`/`PAUSED` (color only for
  status, design language). Keyboard: arrow keys move, Enter opens. Empty state stays, with an "Add app" button.
  Footer: "Add app…" (`?app=new`) and "Manage apps" (`/apps`). Search field when there are more than 8 apps.
- **Add / edit modal** (`?app=new`, `?app=<id>`, `APP_PARAM` in `lib/navigation.ts`, host in the shell like
  `SettingsDialog`): `Stepper` with 1 **Basics** (name, base URL, description, docs link), 2 **Auth & headers**
  (auth kind select, key/value editor with a "secret" toggle; stored secrets show as "•••• set" with Replace),
  3 **Endpoints** ("Read the docs" → proposed endpoints with checkboxes; add manually; **Test call** per endpoint with a
  collapsible JSON preview and the detected items path), 4 **Schedule** (interval presets 15 min / 1 h / 6 h / daily,
  enabled, "let the assistant learn from it"). Editing opens the same steps as tabs.
- **`/apps` and `/apps/[apiId]`** (nav entry in `DATA_NAV`): status, endpoints, mappings (with the pending
  `MAP_API_ENDPOINT` proposal linked), call history, "Call now", "Pause", "Delete".
- Proposals/Findings and the record page show the API origin ("from Shopify · orders") wherever they show "from email"
  today; insights list API evidence next to email evidence.
- Tests: Vitest for `AppsMenu` (list, statuses, empty, keyboard), the modal steps (validation, secret masking, test call
  preview), the detail page; Playwright: add an app against a local stub server, test call, save, appears in the
  dropdown. (The stub needs the guard's test seam: `OutboundRequestGuard` is a bean, `e2e` profile allows one loopback
  port.)

## Steps (each test-first, README in the same change)

| Step | What | Tests |
|---|---|---|
| X1 | ADR 0012 (read-only, guard, per-user, mapping-then-code) + this plan confirmed | – |
| X2 | `OutboundRequestGuard` extracted from `LogoFetcher` (size/time caps, redirect checks; address pinning follows in X4), `LogoFetcher` uses it | unit (private/loopback/link-local/IPv6-mapped rejected, redirect re-check), existing `LogoFetcher` tests stay green |
| X3 | Migration `V1_0_35`, entities, value objects + converters, repositories | value objects, converters, `@RepositoryTest` per repo |
| X4 | `ApiAuthHandler` engine (all kinds except OAuth code) + `ExternalApiClient`; the guard connects to the checked address (DNS rebinding) | one test per handler, registry test, client against a `MockRestServiceServer`/WireMock-style stub |
| X5 | Spec: CRUD + test call + endpoints → regenerate; `ExternalApiService`, mapper, controller, audit | mapper, service (Mockito), `@ControllerTest` incl. 401/404 for another user's app and that secrets never appear in JSON |
| X6 | Frontend: `AppsMenu` in the launcher + add/edit modal (steps 1, 2, 4; manual endpoints + test call) | Vitest; Playwright add-app flow |
| X7 | `ExternalApiPollService`: due calls, backoff, limits, snapshots, items diff | service tests with a fixed `Clock`; `@SpringBootTest` with a stub server |
| X8 | Docs discovery: OpenAPI parser + model fallback (`api-docs-v1`), discovery endpoints, modal step 3 "Read the docs" | parser unit tests on fixture specs, model answer validation (foreign host, `..`, unknown method dropped), `-Pai-eval` set |
| X9 | Mapping: `ApiMappingModelService`, `MAP_API_ENDPOINT` kind + applier, `ExternalApiIngestService`, provenance migration `V1_0_36` | applier unit test, ingest service tests (new/changed/gone, AUTO vs SUGGEST), repository test for `ck_record_source_origin` |
| X10 | Insights from API data: digest with `A<n>` tokens, `evidenceApiItemIds`, InsightApplier links records to API items | `InsightDetectors` rules (e.g. item gone that a record/email refers to), prompt bump `insights-vN` measured with `-Pai-eval` |
| X11 | `/apps` pages, call history, origin badges in proposals/findings/records, command palette entries | Vitest, Playwright |
| X12 | Chat: API items as a retrieval source (`ChatRetrievalService`) | retrieval tests |

## Settled questions (2026-10-28, defaults confirmed)

1. Read-only for the first version: GET plus POST endpoints the user marks as read queries. Write calls come later,
   through proposals.
2. Apps are private per user.
3. No local-network APIs: public HTTPS addresses only. The owner-only `allowPrivateNetwork` flag stays a later option.
4. API keys, tokens, basic auth and OAuth client credentials only. OAuth "sign in with …" comes later.
5. Records from API data follow the `externalApis` AI permission (default `SUGGEST`: proposals first). `AUTO` is
   allowed when the user sets it.
