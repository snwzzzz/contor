# 0002 – Server-side sessions instead of JWT

**Status:** accepted (Phase 1, step 3)

## Context
contor holds a complete mailbox and OAuth tokens for it. It is a single-user, self-hosted app; the browser reaches the
API only through the Next.js server (same origin). We need logout that really ends access, session limits, forced logout
after a password change, and no token readable by JavaScript.

## Decision
Spring Session JDBC (sessions in Postgres), an `HttpOnly`, `SameSite=Strict` session cookie, CSRF protection with the
cookie-to-header pattern, and no JWT anywhere (nothing auth-related in localStorage).

## Why
- Revocation is immediate: logout, the 3-sessions limit, password changes and the absolute lifetime delete or expire
  server-side state. A JWT stays valid until it expires unless we add a deny list (that is, sessions again).
- `HttpOnly` cookies are out of reach of XSS; a bearer token in JS memory or localStorage is not.
- Same origin through the Next.js rewrite means no CORS and no cross-site token handling; CSRF is covered by
  `SameSite=Strict` plus the double-submit token.
- No scaling concern: one instance, sessions in the database it already uses.

## Consequences
- Every mutating request needs `X-XSRF-TOKEN`; the token rotates on login/logout (`GET /api/auth/csrf` refreshes it).
- Login is a JSON endpoint that applies the session strategy itself (see `AuthService`).
- Session data is Java-serialized; the principal (`AuthenticatedUser`) must stay small and `Serializable`.
