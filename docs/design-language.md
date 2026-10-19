# contor design language

The reference is the home page (`contor-web/src/app/(app)/page.tsx`, `src/components/app/home/`). New screens should
look like they belong next to it. If a rule here conflicts with a real need, change the rule here first.

## 1. Principles

1. **Quiet by default.** The UI is a neutral shadcn black and white theme on a plain white page. Content carries the page,
   chrome stays out of the way.
2. **Color is a signal.** It never decorates. If removing a color loses no information, remove it.
3. **Few boxes.** Group with headings, hairlines and whitespace. Use a frame only when several values form one object
   (the stat strip).
4. **Weight is a signal too.** Most text is regular. `font-medium` marks titles and unread items; `font-semibold` is only
   for the page title and big numbers.
5. **Stable layout.** Loading, paging and empty states keep the shape of the final content. Nothing jumps.
6. **Everything clickable looks clickable**: links are underlined, rows and cells show a hover background.

## 2. Tokens

All values live in `contor-web/src/styles/tokens.css` (light and dark), the only place with colors, radii, fonts and
shadows. Components use token classes only (`bg-background`, `text-muted-foreground`, `border`, …), never raw colors.

| Token | Use |
|---|---|
| `--background` / `--surface-page` | The page: plain white (near-black in dark mode). |
| `--surface-raised` | The rare framed panel (same as the page; the border does the work). |
| `--shadow-card` | Barely visible 1px shadow on framed panels. No other shadows on the page. |
| `--primary` | Near-black (white in dark mode): primary buttons, active tab, focus. Not a brand color. |
| `--muted` / `--muted-foreground` | Hover backgrounds, secondary text, icons. |
| `--border` | Hairlines under section headings, panel borders, dividers. |
| `--info` / `--success` / `--warning` / `--destructive` | Status only (see §4). |
| `--tint-1..10`, `--avatar-*` | Collections, records and sender avatars. Not used for page chrome. |
| `--header-height` (`globals.css`) | 3rem top bar. |

Shared classes in `src/lib/card-hover.ts`:

- `SURFACE_CARD`: `rounded-lg border bg-surface-raised shadow-(--shadow-card)` for framed panels.
- `ROW_HOVER`: `hover:bg-muted/50` with a short color transition, for clickable rows and cells. No lift, no border change.

## 3. Typography

Font: Geist (`--font-family-sans`). Tailwind's scale, no custom sizes.

| Role | Class |
|---|---|
| Page title / greeting (h1) | `text-2xl font-semibold tracking-tight` |
| Section heading (h2) | `text-sm font-medium` on a `h-8 border-b` header |
| Row title | `text-sm` (`font-medium` when unread or it is the row's main label) |
| Body | `text-sm` |
| Meta, descriptions, links in headers | `text-xs text-muted-foreground` |
| Big number | `text-2xl font-semibold tracking-tight tabular-nums` |
| Times, counts | add `tabular-nums` |

Use real English labels ("Recent activity", not "Aktuelles"). Use sentence case. Use relative times ("5 min. ago",
`lib/relative-time.ts`) in overviews and absolute times ("14:00") in agendas.

## 4. Color, sparingly

Color appears only as small marks. Never as card or section backgrounds.

| Mark | Meaning |
|---|---|
| `size-1.5 rounded-full bg-info` dot | Something is going on / unread (status line, unread mail). |
| `bg-success` dot | Healthy (mailbox synced, model ready, "All caught up"). |
| `bg-warning` dot | Needs attention soon (mailbox needs sign-in, models missing). |
| `bg-destructive` dot or `text-destructive` text | Broken or overdue (Ollama unreachable, "1 overdue"). |
| `ImportanceBadge` | Only "Urgent" / "High"; normal and low show nothing. |

Icons are lucide, `size-4`, `strokeWidth={1.75}`, `text-muted-foreground`. They sit bare: no tinted tiles and no filled
squares.

## 5. Layout

- Page container: `mx-auto w-full max-w-[85rem]`. The shell (`AppShell`) adds `p-6`, and the page adds `sm:px-2`.
- Vertical rhythm: `gap-8` between blocks, `gap-2` between a section heading and its content, `gap-5` inside the hero.
- Multi-column areas are **one grid with rows**, so each row starts on the same line:
  `grid grid-cols-[minmax(0,1fr)] lg:grid-cols-[repeat(3,minmax(0,1fr))] gap-x-10 gap-y-8 items-start`. The main
  content takes `lg:col-span-2`.
- Always use `minmax(0, 1fr)` tracks (and `min-w-0` on grid children). Otherwise long text widens the column instead
  of truncating.
- Responsive: 4 → 2 → 1 columns for stat-like grids (`sm:grid-cols-2 xl:grid-cols-4`), 3 → 1 for content. No sideways
  scrolling at 390px (e2e guards the home page).

Home page order: hero (greeting, status line, quick actions) → stat strip → row 1 (*Recent activity* | *Today*,
*Connected mailboxes*) → row 2 (*Pending actions* | *AI status*) → *Navigate*.

## 6. Building blocks

| Block | Where | Pattern |
|---|---|---|
| `HomeSection` | `home/HomeSection.tsx` | `<section aria-labelledby>` with a `h-8 border-b` header: h2 left, links right. No box. |
| `SectionLink` | same | `text-xs font-medium`, always underlined (`decoration-muted-foreground/50`, darker on hover); optional trailing `→` when it leaves the page. |
| `QuietEmpty` | same | Muted icon `size-5`, one `text-sm font-medium` line, one `text-xs` muted line, optional `outline xs` button. |
| `Pager` / `usePages` | `home/Pager.tsx` | `‹ 2 / 3 ›` under a list, right-aligned, ghost `icon-xs` buttons. Hidden when everything fits. Clamps when the list shrinks. |
| Stat strip | `home/KpiRow.tsx` | One `SURFACE_CARD` with `gap-px bg-border` for hairlines between cells. Each cell is a whole-cell link: label + small icon, number, one line of context. |
| Status line | `home/StatusBanner.tsx` | One line, not a banner: dot + text + `outline xs` action, optional thin `Progress` (`w-32`). `role="status"`. |
| Quick actions | `home/QuickActions.tsx` | `outline sm` buttons with a muted icon, `font-normal shadow-none`; a ghost "More" opens the command palette (`openCommandPalette()`). |
| List rows | Recent activity, Today, mailboxes | `rounded-md px-2 py-1.5–2.5` + `ROW_HOVER`; the list is `-mx-2` so the hover background bleeds to the column edge while text stays aligned with the heading. Avatars at 24px. |
| Tabs | Recent activity | Owned shadcn `Tabs` with `variant="line"`, `text-xs` triggers. Each tab mounts its own query. |
| Link lists | `home/NavigateLinks.tsx` | Icon + title + one-line description. Hover: `bg-muted`, icon turns `text-foreground`, title underlines (`group-hover:underline`). |
| Top bar | `layout/TopBar.tsx` | 3rem, `bg-background border-b`, ghost icon buttons, logo as a `size-6` black square with a white "c", search fields `bg-muted/50` without border. |

Buttons: `default` (black) only for the single primary action of a row ("Approve"). Use `outline` for secondary actions
and `ghost` for tertiary ones and icon buttons. Inside lists use `size="xs"`, elsewhere `sm`.

## 7. States

- **Loading:** skeletons in the final shape with deterministic widths (`w-3/4`, `w-2/3`, …, never random). Numbers get
  a `h-7 w-12` block, rows get avatar + line skeletons.
- **Empty:** `QuietEmpty` with a reason and, if useful, the next step ("Go to inbox"). Never a blank area.
- **Errors:** `ErrorMessage` in place of the content.
- **Paging:** rows have a fixed height when their content varies (e.g. reasoning clamped to two lines with
  `line-clamp-2 min-h-8 leading-4`). A shorter last page is padded with invisible `aria-hidden` rows, so the section
  height never changes.
- **Time-dependent text** (greeting, "now"): read the clock on the client only (`useSyncExternalStore` with a `null`
  server snapshot, see `useTimeOfDay`), or render it only after data arrived. Never let server and hydration output differ.

## 8. Interaction and accessibility

- Hover = `ROW_HOVER` (rows, cells) or `bg-muted` (link lists). No shadows, lifts or colored borders on hover.
- Focus = `focus-visible:ring-2 focus-visible:ring-ring` on every custom link/row.
- Whole-row and whole-cell links have a readable accessible name (title first). Decorative icons, dots and separators
  are `aria-hidden`.
- Respect `motion-reduce:transition-none` on transitions.
- Visually hidden headings (`sr-only`) are fine where a visible one would be noise (quick actions).

## 9. Do / don't

| Do | Don't |
|---|---|
| Hairlines and whitespace to group | Cards around every section |
| One small status dot | Colored banners, tinted backgrounds, gradients |
| Muted bare icons at `size-4` | Big icons on colored tiles |
| `font-medium` for titles | Bold everywhere |
| Underlined text links | Links that only differ by color |
| Fixed row heights + placeholder rows when paging | Layout that jumps between pages or states |
| Token classes | Hex, `oklch()`, `bg-blue-500` in components |

## 10. Checklist for a new screen

1. Plain page background, `max-w-[85rem]` container, `gap-8` rhythm.
2. Sections are `HomeSection`-style headings over a hairline. Frame something only if it is one object.
3. Color only for status marks; weight only for titles and unread.
4. Lists: `-mx-2` + `ROW_HOVER` rows, `Pager` when they can grow, `QuietEmpty` when empty, shaped skeletons while loading.
5. Grids with `minmax(0,1fr)` tracks; check 1920px and 390px in light and dark (`npm run screenshots`).
6. Grep the diff for raw colors before committing.
