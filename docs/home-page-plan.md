# Home page + top navbar (UI redesign)

Goal: a home page at `/` and an Azure-style shell: a full-width top navbar; navigation opens as a drawer over the page.

## Shell

**Top bar** (`components/layout/TopBar`, full width, `bg-background` + border, shadcn look kept):

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

Azure-style, no boxes, sections stacked: "Welcome to contor", **Quick actions** (row of icons with labels),
**Aktuelles** (preview text only, bold while new), **Actions** (top 3 pending proposals, "View all"),
**Connected mailboxes** (compact rows, address as link, "Manage").

## Tests first
- `TopBar.test.tsx`: launcher opens the empty Apps panel, menu opens the navigation, logo → `/`, chat → `/chat`, settings → `?settings=accounts`.
- `NavDrawer.test.tsx`: closed by default, all links, search filter and "No pages found", closes on navigation/Escape, Ctrl+B, badges, current page, collection reordering.
- `home/*.test.tsx`: quick action hrefs; news items bold until opened; proposals list + view all + empty state; mailboxes as links + manage link + empty state.
- e2e `shell.spec.ts`: drawer navigation and search, top bar links, home page sections.

## Docs
README frontend section: home page, navbar, chat placeholder.
