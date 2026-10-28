# 0012 – External APIs ("Apps")

**Status:** accepted (plan: `docs/external-apis-plan.md`)

## Context
contor learns from mail. Users also want it to learn from other systems: a shop's orders, a job platform, a parcel
tracker, any HTTP API. That means contor calls hosts that the user names, and the model reads what comes back. Both
are new risks. A configured URL could point at the machine itself (Ollama, Postgres, Spring) or the local network
(SSRF). An API answer is untrusted text that reaches a prompt. API keys are secrets that must not leak to the web app,
logs or prompts. The single GPU thread cannot afford a model call per polled item.

## Decision
1. **One outbound guard.** Every request to a host from user input or the web goes through
   `security/OutboundRequestGuard`. It allows HTTPS on port 443 only, with no user info. Each host, including every
   redirect target (at most 3 redirects), must resolve to public addresses only. Each request has a timeout and a cap
   on the bytes read. Error messages and logs name the host, never the full URL (query strings may carry API keys).
   The sender logo fetch was the first user and moved onto it. Local-network APIs are not allowed. An owner-only
   opt-in per app may come later.
2. **Read-only.** contor sends `GET`, plus `POST` only to endpoints the user marked as read queries (search, GraphQL).
   The model never chooses a method, host or path at runtime. It can only pick among endpoints the user confirmed.
   Writing to an external API is a later step and will go through a proposal.
3. **Private per user**, like mailboxes. An app belongs to its owner, other users get 404. Records made from API data
   are shared data, like records made from mail.
4. **Secrets stay in the backend.** API keys, tokens, client secrets and secret header values are stored in an
   encrypted column (`EncryptedStringConverter`). They are write-only in the API, never logged, never put in a prompt
   and never stored with a response.
5. **Responses are untrusted input**, like email bodies. They enter prompts as quoted data with source tokens, and the
   model's answer is validated before anything is stored.
6. **Learn once, then map in code.** The model reads the documentation once (an OpenAPI document is parsed without the
   model). It proposes a mapping from an endpoint's items to a collection once, and the user approves it. Regular
   polls apply that mapping in code. An unchanged response (same hash) costs nothing. Only new or changed items reach
   the mapper and the insight pass.
7. **Polling with limits.** Each app has its own interval (at least 15 minutes). Retries back off exponentially, and
   `Retry-After` is honoured. Each app has a daily request limit. 401/403 stops polling until the user fixes the
   credentials. HTTP calls run on their own small executor, and model work queues on the AI thread behind mail.

## Consequences
- **DNS rebinding is closed by using one lookup.** The guard runs on Apache HttpClient 5 (version managed by Spring
  Boot). Its only `DnsResolver` is `security/PublicAddressResolver`, which returns a host's addresses only when all of
  them are public, and the connection goes to exactly those addresses. No proxy, redirects, retries or cookies. A body
  over the cap is aborted, not read to the end. (X2 used the JDK client, which looks the host up again when it
  connects, so a host could answer the check with a public address and the connection with a private one. X4 replaced
  it.)
- With `httpclient5` on the classpath, Spring's `RestClient.builder()` picks it by default. The Gmail and Graph OAuth
  and mailbox clients now run on it too. Ollama sets its own JDK request factory.
- Auth kinds are a plug-in engine (`externalapi/ApiAuthHandler` + `ApiAuthRegistry`). Client-credentials tokens are
  fetched through the guard and kept in memory until shortly before they expire.
- New proposal kind `MAP_API_ENDPOINT`. `record_source` gains a second origin (API item next to email), and insights
  can cite API items as evidence.
- The top bar's Apps menu lists the user's apps. Adding and editing happen in a modal.
