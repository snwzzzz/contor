# 0001 – Frontend API client: orval

**Status:** accepted (Phase 1, step 1)

## Context
The frontend client must be generated from `api-spec/openapi.yaml`, typed, and integrate with TanStack Query. It must send same-origin credentials and the CSRF header on every mutating request, and turn `ErrorResponse` bodies into one error type for toasts. Candidates: orval, openapi-typescript + openapi-fetch.

## Decision
Use **orval** (`client: react-query`, `httpClient: fetch`, `mode: tags-split`, no index/barrel files) with a hand-written mutator `src/lib/api/fetcher.ts`.

## Why
- orval generates the TanStack Query hooks and query keys directly; with openapi-fetch we would hand-write (and keep in sync) a hook and key factory per operation, which is exactly the drift contract-first is meant to avoid.
- The mutator is a single hand-written seam for credentials, CSRF and `ApiError`, tested on its own.
- `indexFiles: false` and `tags-split` keep imports module-specific (no barrel imports, per the React best-practices skill), so only the hooks a page uses are bundled.

## Consequences
- Generated code lives in `src/lib/api/generated/`, is gitignored, ESLint-ignored and regenerated before `dev`, `build`, `test` and `typecheck`.
- Generated functions return the response body directly (`includeHttpResponseReturnType: false`); errors are thrown as `ApiError(message, status)`.
- openapi-fetch's smaller runtime is given up; acceptable for a local single-user app.
