# 0005 – Nonce-based CSP, every page rendered per request

**Status:** accepted (Phase 1, step 6)

## Context
The frontend displays mail content and AI-generated data; a script injection would expose the whole mailbox.
Next.js 16 scaffolds with `cacheComponents` (partial prerendering), which serves static shells built ahead of time.

## Decision
`src/proxy.ts` sets a strict Content-Security-Policy on every page request: `script-src 'self' 'nonce-…' 'strict-dynamic'`,
`object-src 'none'`, `frame-ancestors 'none'`, `base-uri 'self'`, `form-action 'self'`. Next.js applies the nonce to its
own scripts; the theme script receives it explicitly. `cacheComponents`/PPR are disabled so every page is rendered per
request. `style-src` allows inline styles because Radix positions popovers with inline style attributes.

## Why
- A nonce must be unique per request, which static shells cannot carry (PPR is incompatible with nonce CSP).
- Every page is per-user behind a session, so static prerendering would gain little.
- Inline styles cannot execute code; scripts are the attack surface that matters.

## Consequences
- All routes are dynamic (`ƒ` in the build output); fine for a single-user app.
- Development needs `'unsafe-eval'` (React debugging) and `ws:` for HMR; production does not
  (`npm run test:e2e:prod` checks that pages load without CSP violations).
