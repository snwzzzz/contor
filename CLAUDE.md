# CLAUDE.md – contor

contor is a personal, self-hosted management platform: Outlook inbox sync (later), a local Ollama model proposing actions (later), and dynamic user/AI-defined collections with relations between records.

## 0. Working rules (non-negotiable)

1. **Tests first, always.** For every feature, endpoint, service or component: write the tests first, run them and confirm they fail for the right reason, then write the code that makes them pass, then refactor. No production code without a failing test that requires it. Backend: JUnit 5, AssertJ, Mockito, Spring test slices, Testcontainers. Frontend: Vitest + Testing Library; Playwright for the auth and collection flows.
2. **Java style follows Appendix A** (condensed below). The contor deviations win over the template. Anything not covered: ask. `reference/java-style/` may hold the original project as extra reference; Appendix A and its deviations take precedence.
3. **API contract first, generated code is untouchable.** `api-spec/openapi.yaml` is the single source of truth. Backend interfaces/models (openapi-generator Maven plugin → `contor-api/target/generated-sources/openapi`) and the frontend client (orval → `contor-web/src/lib/api/generated/`) are generated from it, never edited by hand, never committed. To change the API: change the spec, regenerate. Controllers implement the generated `XApi` interfaces.
4. **README stays current.** After every major change (new step, env var, command, dependency, changed setup), update `README.md` in the same change. A step is not done until the README reflects it.
5. **React/Next.js best practices, always.** All frontend code follows the `vercel-react-best-practices` skill (installed in `.claude/skills/`). Install/refresh with:
   ```
   npx skills add https://github.com/vercel-labs/agent-skills --skill vercel-react-best-practices
   ```
   Read it before writing frontend code; apply it to every component, hook, route and data-fetching decision. Where it conflicts with the spec, ask.

## 1. Repository

| Path | What |
|---|---|
| `api-spec/openapi.yaml` | API contract (OpenAPI 3.0.3) |
| `contor-api/` | Spring Boot 4.1 / Java 21 / Maven backend, base package `com.example.contor` |
| `contor-web/` | Next.js 16 (App Router) / TypeScript strict frontend |
| `infra/docker-compose.yml` | Postgres 18 + pgvector, bound to 127.0.0.1 |
| `docs/architecture.md`, `docs/decisions/` | architecture + ADRs |

Commands: see README. Backend tests need Docker (Testcontainers). `./mvnw test` in `contor-api`; `npm test`, `npm run lint`, `npm run typecheck`, `npm run test:e2e` in `contor-web`.

## 2. Java style (condensed Appendix A)

### Deviations for contor (win over everything below)
1. **API models are generated from OpenAPI.** Schemas are named `NewXCommand`, `UpdateXCommand` (nullable fields, `null` = don't change), `XDto` (plain `Long` IDs). Generated models are mutable classes with getters (`dto.getName()`), not records. Controllers implement the generated `XApi` interface and stay one-line delegations to the service. Mappers map entity → generated `XDto`.
2. **Tests run against real Postgres via Testcontainers, never H2** (pgvector, JSONB, trigram, transactional DDL). Use `PostgresTestcontainersConfiguration` (reusable container).
3. **Security is section 6 of the spec** (see §5 below): no `permitAll`, CSRF on, no CORS, Argon2id.
4. **Timestamps are `timestamptz`** / Java `Instant`. The JVM runs in UTC (`ContorApplication.main`, surefire `-Duser.timezone=UTC`).
5. **Consistency fixes:** every not-found exception extends `ResourceNotFoundException` (→ 404); imports, never inline FQCNs; lowercase packages (`exception`, `validation`); exactly one web security config, no CORS config at all.
6. **Converter tests are plain unit tests** (no `@DataJpaTest`).

### API enum names
API enum schemas must not share a name with a domain enum, or mappers would need inline FQNs. Existing pairs:
`UserRole`→`Role`, `CreatedBy`→`Creator`, `FieldType`→`CollectionFieldType`, `ProposalKind`→`ProposalAction`,
`ProposalStatus`→`ProposalState`, `LabelSource`→`LabelOrigin`, `RelationCreatedBy`→`RelationOrigin`,
`ProcessingState`→`EmailProcessingState`, `MailAccountStatus`→`MailAccountState`, `MailProvider`→`MailProviderKind`,
`ReplyDraftStatus`→`ReplyDraftState`, `ProposalImportance` (domain)→`ProposalPriority` (API).
Map with `ApiEnum.valueOf(domain.name())` and back.

### Packages (by layer, under `com.example.contor`)
`domain` (entities, value objects, enums) · `mapper` (static entity → generated DTO) · `persistence` (Spring Data repos) · `persistence/converter` (AttributeConverters, encryption) · `persistence/dynamic` (jOOQ for generated collection tables) · `service` · `controller` (+ `GlobalExceptionHandler`) · `exception` · `validation` · `config` · `security` · `fieldtype` (plug-in engine) · `proposal` (plug-in engine) · `ai` (interfaces only). Generated: `api` (`XApi`), `api.model`.

Flow: `Generated XApi ◀ Controller ─(NewXCommand)▶ Service ▶ Repository ▶ Entity`, back via `XMapper.toDto(entity)`. Entities never leave the service layer.

### Entities
- `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder @Entity @Table(name = "\"x\"")` (table names quoted), extends `AuditableEntity` (`createdAt`/`lastModified` via `@PrePersist`/`@PreUpdate`).
- Typed IDs: `@EmbeddedId XId id;` with nested `@Embeddable public record XId(@GeneratedValue Long id) {}`. Repos: `JpaRepository<X, X.XId>`, derived queries through the ID (`findByParent_Id(Parent.ParentId)`). Wrap IDs in the service (`new X.XId(id)`), unwrap in the mapper with null checks.
- Relations: `LAZY @ManyToOne` + explicit `@JoinColumn`; `@OneToMany(mappedBy, orphanRemoval)` with `@Builder.Default`.
- Child/detail tables: plain `@Id Long` with `IDENTITY`, no auditing (or composite key where the spec says so).
- Flexible data: `@JdbcTypeCode(SqlTypes.JSON)`.

### Value objects
Records validating in the compact constructor (null/empty/regex) and throwing `IllegalArgumentException`; each has an `@Converter(autoApply = true)` `AttributeConverter` in `persistence/converter` storing a plain string.

### Mappers
Only `public static XDto toDto(X entity)`; `null` in → `null` out; null-safe ID unwrapping. Hand-written, no MapStruct.

### Services
`@RequiredArgsConstructor @Service`, `private final` repos, every public method `@Transactional` (`jakarta.transaction.Transactional`), reads included. Flow: load related via `findById(new XId(..)).orElseThrow(() -> new XNotFoundException(..))` → business rules (`DuplicateResourceException`) → `X.builder()` → `saveAndFlush` → `XMapper.toDto`. Names: `createX`, `findAll`, `findById`, `findByParentId`, `updateX`, `deleteX`.

### Controllers
`@RequiredArgsConstructor @RestController implements XApi`; no logic, one line per method; `201` for POST, `204` for DELETE, `ok(...)` otherwise. Paths come from the spec: `/api/<plural>`, children nested (`/api/collections/{id}/fields`).

### Errors
One `XNotFoundException extends ResourceNotFoundException` per resource. State conflicts (already decided, still in use) extend `ConflictException` (409), like `DuplicateResourceException`. `GlobalExceptionHandler` returns `ErrorResponse(message, status)` (an OpenAPI schema): not found 404, `DuplicateResourceException` 409, `PasswordValidationException`/`IllegalArgumentException` 400, service unavailable 503, anything else 500 with a generic message (logged server-side). Only touch the handler for a new status code.

### Plug-in engines
Strategy + registry: an interface (Javadoc says "add a bean to extend"), one `@Component` per implementation, a registry building a map from the injected `List<...>` keyed by type. contor: `FieldTypeHandler`/`FieldTypeRegistry`, `ProposalApplier`/`ProposalApplierRegistry`.

### Tests (packages mirror main; one style per layer)
| Layer | Style |
|---|---|
| value objects | plain JUnit 5 + AssertJ (`assertThatThrownBy(..).isInstanceOf(IllegalArgumentException.class)`) |
| converters | instantiate directly; both directions + null |
| mappers | plain JUnit; builder + typed IDs; fields, null input, null relations |
| persistence | `@RepositoryTest` (= `@DataJpaTest` + Testcontainers + `CryptoConfig` + display names); real entity chain, derived queries. Unsaved test entities come from `persistence/Fixtures`. One failing SQL statement aborts the test transaction: one DB-error assertion per test |
| services | `@ExtendWith(MockitoExtension.class)`, `@Mock` repos, `@InjectMocks`; `thenAnswer(inv -> inv.getArgument(0))`; `verify(.., never())` on rejected paths |
| controllers | `@ControllerTest(XController.class)` (= `@WebMvcTest` + real `SecurityConfig`) + MockMvc + `@MockitoBean` service; authenticate with `TestAuth.owner()`/`TestAuth.user()`, add `csrf()` on mutating requests; status codes + JSON body incl. errors and 401/403. Never assert cookies here: `csrf()` swaps the shared CSRF repository for a session-based one (test-order dependent) |
| plug-ins | one unit test per handler + registry resolution test (`fieldtype/FieldFixtures` builds fields named `f` and jOOQ records; `TestFieldTypes.registry()` gives services all real handlers) |
| dynamic tables (jOOQ) | `@DynamicTableTest` (= `@JooqTest` + Flyway + Testcontainers, rolled back incl. DDL) with `@Import` of the class under test |
| integration | `@SpringBootTest` + Testcontainers for end-to-end flows, `contextLoads`. Security/HTTP behaviour (cookies, CSRF, sessions) over real HTTP with `integration/ApiClient` (`RANDOM_PORT`); truncate `app_user`/`spring_session` in `@BeforeEach` (TRUNCATE bypasses the append-only triggers) |

Naming: snake_case + `@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)`. **Exception:** service tests use camelCase with an underscore scenario suffix (`createTag_DuplicateNameRejected`, `findById_NotFound`).

### New-resource checklist
1. `V1_0_N__add_<things>.sql`: quoted table, `<thing>_seq` (increment by 50), `created_at`/`last_modified` `timestamptz`, named constraints (`fk_<table>_<target>`, `uq_<table>_<cols>`), indexes on FKs and filter columns, enums as `text` + `CHECK`, a comment explaining the table
2. `domain/` entity with nested `XId` record, extends `AuditableEntity`, plus value objects
3. `persistence/converter/` converter per value object
4. `persistence/XRepository extends JpaRepository<X, X.XId>`
5. `exception/XNotFoundException extends ResourceNotFoundException`
6. `api-spec/openapi.yaml`: paths, `NewXCommand`, `UpdateXCommand`, `XDto`, `ErrorResponse` for errors → regenerate
7. `mapper/XMapper.toDto`
8. `service/XService`
9. `controller/XController implements XApi`
10. Tests first, one per layer (above)
11. Update `README.md`

## 3. Data rules
- **Migrations only via Flyway** (`contor-api/src/main/resources/db`, `V1_0_N__description.sql`, one concern each). Hibernate `ddl-auto=validate`; never let JPA create schema.
- **Generated collection tables (`c_<slug>`) are not JPA entities.** Access them only through `persistence/dynamic/` with the jOOQ DSL. Never string-concatenate SQL. Identifiers come only from validated `Slug` value objects (`DynamicNames` builds table/constraint names, ≤ 63 chars).
- Always select/bind generated-table columns as **typed** jOOQ fields (`DSL.field(name(col), dataType)` from the field handler's `ColumnSpec`); jOOQ has no metadata for these tables, so untyped reads return JDBC types (`java.sql.Date`).
- System columns of every generated table: `id, title, status, extra (jsonb), created_at, last_modified, created_by`; field slugs may not use them.
- New field type = enum constant in `domain/FieldType` + one `@Component FieldTypeHandler` (+ its unit test) + the value shape in the spec's `CollectionFieldType` description. Handlers throw `IllegalArgumentException` with a message that does not name the field.
- Schema changes go through `CollectionSchemaService` only (metadata + DDL + `schema_version` + `collection_schema_change` + audit in one transaction). Record writes go through `RecordService` (validation collects all violations → `RecordValidationException`).
- Services persist with `saveAndFlush` (also the audit log), so constraint errors surface inside the operation.
- New proposal kind = one `@Component ProposalApplier` (+ unit test) and its payload format in the spec's `ProposalAction` description. Appliers read `proposal.getFinalPayload()`, throw `IllegalArgumentException` for malformed payloads, run inside the decision transaction, and set `requiresOwner()` for schema/settings changes. Records created by appliers have `created_by = AI` and a `record_source` link to the source email.
- Mail is scoped to the current user's mailboxes (`EmailSpecifications.Criteria.ownerId` is mandatory); never map tokens to DTOs.
- Record IDs are unique per collection table only: any reference from outside is always `(collection_id, record_id)`.
- Enums: `text` + named `CHECK` (`ck_<table>_<column>`), mapped with `@Enumerated(EnumType.STRING)`. JSON: `jsonb` + `@JdbcTypeCode(SqlTypes.JSON)` (prefer records/lists over raw maps where the shape is known).
- Sequences are `<table>_seq` (increment 50); Hibernate derives the name from the entity name, so entity class names must match table names in CamelCase (`AppUser` → `app_user_seq`).
- `*_enc` columns: `@Convert(converter = EncryptedStringConverter.class)` on a plain `String` field (never auto-applied).
- Append-only tables (`audit_log`, `collection_schema_change`) are `@Immutable` entities with a `forbid_update_delete()` trigger.
- `app_readonly` (NOLOGIN) gets `SELECT` per generated collection table, granted by the collection engine; never on fixed tables.

## 4. Frontend rules
- **Redesign-ready layering:** tokens in `src/styles/tokens.css` (the only place with colors/radii/fonts) → owned shadcn primitives `components/ui/` → app composites `components/app/` → shell `components/layout/` → pages `src/app/` (composition only). Pages never import `components/ui` (ESLint `no-restricted-imports` on `src/app/**`). No hard-coded colors anywhere; use token classes (`bg-primary`, `text-muted-foreground`).
- Edits to owned `components/ui` files are marked with a `// contor:` comment (e.g. deterministic sidebar skeleton width, CommandDialog title inside the content, `use-mobile` via `useSyncExternalStore`) so a later `shadcn add --overwrite` does not silently undo them.
- API access only through the generated orval hooks (`src/lib/api/generated/endpoints/<tag>/<tag>.ts`, import the concrete module, no barrels) and the `src/lib/api/fetcher.ts` mutator (same-origin, CSRF header, fetches the CSRF cookie when missing, throws `ApiError`). Errors are typed `ApiError` (`ErrorType` export in the mutator).
- `src/lib/api/query-client.ts`: any 401 → login with `next`; failed mutations → toast. Opt out per call with `meta: { skipAuthRedirect: true }` (401 is an expected answer, e.g. login) and `meta: { silent: true }` (the component shows the error).
- Logout uses a full page load (`hardNavigate("/login")`): clears in-memory data and avoids redirect races.
- `src/proxy.ts` (Next 16's middleware): per-request CSP nonce + optimistic session-cookie check (redirect to `/login?next=`). The backend stays the authority. Pages are all dynamic because of the nonce: do not enable `cacheComponents`/PPR (ADR 0005).
- Server-only helpers live in `src/lib/server/` (`import "server-only"`), e.g. the setup-status check the login/setup pages run before rendering.
- `CONTOR_API_URL` is baked into the `/api` rewrite at build time.
- Tests: Vitest + Testing Library with `src/test/render.tsx` (`renderWithProviders`), `src/test/api-mock.ts` (`mockApi({"GET /api/...": {...}})`), `vi.mock("next/navigation", () => import("@/test/next-navigation"))`. Playwright runs against the real backend (`e2e/support/start-backend.sh`); the `setup` project does first-run setup and stores the owner session in `e2e/.tmp/owner.json`. Wait for data that proves hydration before keyboard shortcuts. A console-error test guards hydration mismatches; a CSP test guards nonce wiring.
- Next.js 16: read `contor-web/node_modules/next/dist/docs/` before using Next APIs; this version differs from older training data. Typed route props (`PageProps<"/x">`, `LayoutProps<"/">`) come from `next typegen` (part of `npm run typecheck`).
- TypeScript strict, no `any` in our code, every change checked against the React best-practices skill.

## 5. Security rules
- Session auth (Spring Session JDBC), no JWT, nothing in localStorage. Cookie `HttpOnly`, `SameSite=Strict`, `Secure` on HTTPS, 30 min idle, 12 h absolute, session fixation protection, max 3 sessions/user.
- CSRF on: `CookieCsrfTokenRepository` + SPA handler; frontend sends `X-XSRF-TOKEN`. No CORS anywhere: Next.js rewrites `/api/*` to Spring.
- Deny by default; permit only `/api/auth/login`, `/api/auth/csrf`, `/api/setup` (while no user exists), `/actuator/health`. `@PreAuthorize` for owner-only operations. Swagger UI only in `dev` and still authenticated.
- Argon2id passwords; password rules collect all violations; per-username lockout + per-IP rate limit; identical error for unknown user/wrong password/locked.
- First run: one-time setup token printed to the log; no default credentials anywhere.
- `*_enc` columns: AES-256-GCM `AttributeConverter`, key from `APP_ENCRYPTION_KEY`, random IV, key-version prefix; app refuses to start without a valid key.
- Never log passwords, tokens, email bodies or full addresses (use the log-masking helper).
- Spring and Postgres bind to 127.0.0.1; Ollama must stay on localhost. Actuator exposes only `health` without details.
- Errors always as `ErrorResponse`, never stack traces. Audit log for auth events, 2FA/password changes, schema changes, proposal decisions.

### How it is built (keep it this way)
- `config/SecurityConfig` is the only web security config: the API chain (`@Order(2)`) and a dev-only Swagger chain (`@Order(1)`). New public endpoints must be added to its `authorizeHttpRequests` explicitly and to the spec with `security: []`.
- Login is the JSON endpoint in `AuthService`, not `formLogin`: it calls the `AuthenticationManager`, then the `SessionAuthenticationStrategy` bean (session limit, id rotation, registration, CSRF refresh) and saves the context via the `SecurityContextRepository` bean. Reuse these beans for any new login step (TOTP).
- The session principal is `security/AuthenticatedUser` (serializable, no password). Get the current user from it, never from request input.
- Audit via `AuditService.record(AuditEvent...)`; it joins the caller's transaction. Add new actions to `domain/AuditAction`.
- Owner-only operations: `@PreAuthorize("hasRole('OWNER')")` on the **controller** method (services are also called by the seeder and, later, by proposal appliers without a user). Method security uses class proxies (`proxyTargetClass = true`): an interface proxy would hide `@RestController` on controllers implementing generated interfaces.
- Only loopback is a trusted proxy for `X-Forwarded-For` (`server.tomcat.remoteip.internal-proxies`).
