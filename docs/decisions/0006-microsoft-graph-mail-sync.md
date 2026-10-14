# 0006 – Microsoft Graph API integration and mail sync

**Status:** accepted (Phase 1, step 7)

## Context
contor needs to connect to Microsoft 365 / Outlook accounts via Microsoft Graph to sync incoming emails,
track message states, and support AI-driven classification and proposals in later phases.
The application uses session-based authentication (Spring Session JDBC) with a single `SecurityConfig`,
and all tokens must remain strictly on the backend, encrypted at rest.

## Decision
1. **OAuth2 Authorization Code Flow with MSAL4J**: Instead of pulling in `spring-boot-starter-oauth2-client`
   or `spring-cloud-azure-*` (which introduce complex auto-configured security filters and CSRF handling that
   clash with contor's session architecture), we use `com.microsoft.azure:msal4j` directly in dedicated services
   (`MsGraphAuthService`).
2. **Microsoft Graph Java SDK (v6)**: We use the official `com.microsoft.graph:microsoft-graph` SDK to query
   messages and execute delta sync (`/me/mailFolders/inbox/messages/delta`).
3. **Encrypted Token Storage**: Tokens are stored encrypted in the existing `mail_account` table
   (`access_token_enc`, `refresh_token_enc`) via `EncryptedStringConverter` (AES-256-GCM). They are never exposed to the API.
4. **State Nonce & Redirect Callback**: OAuth2 `state` nonces are generated and verified on callback. Upon token
   acquisition, the callback returns an HTTP 302 redirecting the browser to `/settings/mail`.
5. **Scheduled + On-Demand Sync**: Sync can be triggered manually via `POST /api/mail/accounts/{id}/sync` and
   runs automatically in the background on a configurable schedule (`contor.mail.sync-interval`).

## Consequences
- No filter conflicts with Spring Security.
- The `openapi.yaml` contract defines routes for connect, callback, sync, disconnect, and status.
- Tokens are automatically refreshed on-demand and during scheduled sync runs.
