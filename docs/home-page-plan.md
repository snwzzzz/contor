# Home page + top navbar (UI redesign)

Goal: a home page at `/` and an Azure-style shell: a full-width top navbar; navigation opens as a drawer over the page.

## Shell

**Top bar** (`components/layout/TopBar`, full width, compact 3rem, white with a hairline below):

| Position | Item | Behaviour |
|---|---|---|
| left | App launcher (grid icon) | Opens an "Apps" panel (dropdown). Empty for now ("No apps yet"). |
| left | Menu (hamburger) | Opens the navigation drawer (also Ctrl/Cmd B). |
| left | contor logo | Link to `/`. |
| middle | Global search | unchanged |
| right | Command palette, processing indicator | unchanged (hidden on phones) |
| right | Chat | Link to `/chat` (empty placeholder page, future feature). |
| right | Settings | Opens the settings modal (`?settings=accounts`). |
| right | Account | Existing `UserMenu`. |

**Navigation drawer** (`components/layout/NavDrawer`, replaces the persistent sidebar): a panel over the page, starting
below the top bar (`--header-height` in `globals.css`); the page does not move. Clear overlay (no dim, no blur); closes on
outside click, Escape, choosing a page, or Ctrl/Cmd B. A search field on top filters pages and collections. Rows have no
gaps: Home, Inbox, Calendar, To-dos / AI insights (Proposals, Findings with counts) / Collections (drag or Alt+Up/Down
to reorder when not searching) / Graph & automation. The sidebar width and resize handle are gone.

## Home page (`app/(app)/page.tsx`)

Quiet shadcn-style layout on a white page, neutral black/white theme, color only for small status marks. Max. 1360 px
wide. Greeting by time of day with a one-line status and outline quick-action buttons (*More* opens the command palette),
a hairline-divided strip of four numbers, then unboxed sections (*Recent activity* with tabs, *Pending actions* |
*Today*, *Connected mailboxes*, *AI status*) and *Navigate* links. Unread counts use `GET /api/emails?read=false` (added
for this); "Flagged" became *Needs reply* (`answered=false`) because emails have no flag; the greeting uses the username
(no display name in the API).

## Tests first
- `TopBar.test.tsx`: launcher opens the empty Apps panel, menu opens the navigation, logo → `/`, chat → `/chat`, settings → `?settings=accounts`.
- `NavDrawer.test.tsx`: closed by default, all links, search filter and "No pages found", closes on navigation/Escape, Ctrl+B, badges, current page, collection reordering.
- `home/*.test.tsx`: greeting + status banner states, stat cards (numbers, links, skeletons), quick-action tiles + *More*, recent activity tabs/unread/empty, pending actions (inline approve), today, mailboxes (sync state), AI status, navigate links.
- e2e `shell.spec.ts`: drawer navigation and search, top bar links, home page cards and tabs, no sideways scrolling at 390 px.

## Docs
README frontend section: home page, navbar, chat placeholder.
