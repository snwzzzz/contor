# 0011 – Mail provider abstraction and Gmail

**Status:** accepted (extends 0006)

## Context
Mail was synced only from Outlook, with Microsoft Graph SDK types used directly in `MailService` and `MailSyncService`.
Gmail is the second provider, and more may follow (IMAP, Fastmail). Adding each provider by branching inside the
services would spread provider details through the sync, the OAuth callback and the tests.

## Decision
1. **Strategy + registry, like the field types.** `mailprovider/MailOAuthClient` (sign-in URL, code exchange, refresh)
   and `mailprovider/MailboxClient` (profile, photos, `fetchChanges(token, folder, cursor)`) are interfaces with one
   `@Component` per provider. `MailProviderRegistry` collects them keyed by `MailProvider` and fails at startup when
   a provider lacks one of the two. The services only see the registry and provider-neutral records (`SyncedMessage`,
   `FolderChanges`, `OAuthTokens`, `MailboxProfile`, `ContactPhotoData`).
2. **Opaque sync cursors.** `mail_account.sync_cursor` / `sent_sync_cursor` (renamed from `delta_link`) hold whatever
   the client hands back: a Graph delta link, or a Gmail history id. Page cursors are opaque too. A cursor the provider
   no longer accepts raises `SyncCursorExpiredException` (Graph 410, Gmail history 404), and the sync restarts that
   folder from scratch.
3. **One callback for every provider, with PKCE.** `OAuthStateCodec` encrypts user id, provider, a PKCE verifier,
   a nonce and the issue time into the `state` (15 minutes valid). The callback reads the provider from the state, so
   there is one public endpoint and no change to `SecurityConfig`. PKCE (S256) is used for both providers.
4. **Gmail through REST, not the Google client library.** `RestClient` plus small records (`GmailJson`) cover the
   few calls needed (`profile`, `messages.list`, `messages.get?format=full`, `history.list`, OpenID userinfo, People
   `connections`). This adds no dependency (no Gson, no Google HTTP stack next to Spring's) and is tested with
   `MockRestServiceServer`. The first sync lists the label within `GMAIL_INITIAL_SYNC_DAYS` and ends with the history
   id read before listing; later syncs read `history.list`. Messages are fetched with at most 4 requests in flight
   (quota: 250 units per user and second, `messages.get` costs 5), and 429/5xx answers are retried with backoff.
   `GmailMessageParser` walks the MIME tree (charsets, base64url, RFC 2047 headers, attachments vs. inline images).
5. **No push.** Gmail push needs Pub/Sub and a public endpoint; contor stays on localhost and keeps polling.

## Consequences
- A new provider adds a `MailProvider` constant, a migration line for `ck_mail_account_provider`, the
  `MailProviderKind` value in the spec, a properties record and two `@Component`s. Services and web app stay unchanged.
- `GET /api/mail/providers` tells the web app which providers are configured. The connect buttons follow it.
- States issued before this change (old format) are rejected. Connecting again fixes that.
- Google refresh tokens of an app in *Testing* status expire after 7 days (README, *Mail setup*).
