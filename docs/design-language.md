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
squares. The one exception is the page icon in the page header: `size-6`, bare, in its section's vivid tint
(`tintVividClass`), so pages are recognizable at a glance.

## 5. Layout

- Page container: `mx-auto w-full max-w-[85rem]`. The shell (`AppShell`) adds `px-6 pt-3 pb-6` and `gap-5` between the header and content. The home page adds `sm:px-2`.
- Vertical rhythm: `gap-8` between blocks, `gap-2` between a section heading and its content, `gap-5` inside the hero.
- Multi-column areas are **one grid with rows**, so each row starts on the same line:
  `grid grid-cols-[minmax(0,1fr)] lg:grid-cols-[repeat(3,minmax(0,1fr))] gap-x-10 gap-y-8 items-start`. The main
  content takes `lg:col-span-2`.
- List + detail inside a page (record emails): the page scrolls with the list; the detail pane is `lg:sticky lg:top-0` with the viewport height and scrolls its own content. No fixed-height panes sized with `calc(100svh - …)` guesses.
- Side panels next to long content (report sources) are `lg:sticky lg:top-4 lg:self-start`: only as tall as their content, so they stay in view; never a scroll area of their own, and nothing in them is clamped or truncated (cited text, names and surrounding paragraphs wrap in full).
- A scroll area that scrolls vertically also gets `overflow-x-hidden`, and the column inside it `grid-cols-[minmax(0,1fr)]`; wide content (tables) scrolls in its own box.
- Always use `minmax(0, 1fr)` tracks (and `min-w-0` on grid children), also for a single-column grid inside a row (`grid-cols-[minmax(0,1fr)]`), e.g. the text column of an email row. Otherwise long text widens the column instead
  of truncating.
- Responsive: 4 → 2 → 1 columns for stat-like grids (`sm:grid-cols-2 xl:grid-cols-4`), 3 → 1 for content. No sideways
  scrolling at 390px (e2e guards the home page).

Home page order: hero (greeting, status line) → stat strip → *Navigate* → row 1 (*Recent activity* | *Today*,
*Connected mailboxes*) → row 2 (*Pending actions* | *AI status*).

## 6. Building blocks

| Block | Where | Pattern |
|---|---|---|
| `HomeSection` | `home/HomeSection.tsx` | `<section aria-labelledby>` with a `h-8 border-b` header: h2 left, links right. No box. |
| `SectionLink` | same | `text-xs font-medium`, always underlined (`decoration-muted-foreground/50`, darker on hover); optional trailing `→` when it leaves the page. |
| `QuietEmpty` | same | Muted icon `size-5`, one `text-sm font-medium` line, one `text-xs` muted line, optional `outline xs` button. |
| `Pager` / `usePages` | `home/Pager.tsx` | `‹ 2 / 3 ›` under a list, right-aligned, ghost `icon-xs` buttons. Hidden when everything fits. Clamps when the list shrinks. |
| Stat strip | `home/KpiRow.tsx` | One `SURFACE_CARD` with `gap-px bg-border` for hairlines between cells. Each cell is a whole-cell link: label + small icon, number, one line of context. |
| Status line | `home/StatusBanner.tsx` | One line, not a banner: dot + text + `outline xs` action, optional thin `Progress` (`w-32`). `role="status"`. |
| Settings sections | `settings/SettingsSection.tsx` | A settings page with several parts (Autonomy: Setup, Mailboxes, Permissions, Inbox analysis) splits them with a small `font-medium` heading over a hairline (`h-8 border-b`), like the home page's sections; no cards. |
| Settings rows | `settings/GeneralSettings.tsx` | A setting is a plain row: label (`font-medium`) with a muted hint on the left, the control on the right, rows divided by hairlines (`divide-y`), no cards. Few fixed choices (theme) are a segmented radio group on `bg-muted` (the checked one on `bg-background`); longer lists are a `NativeSelect` (`settings/SettingsRow.tsx`: `SettingsRow`, `SETTINGS_SELECT` = its width). |
| Loading | `app/LoadingSkeletons.tsx`, `layout/LoadingBar.tsx`, `app/(app)/loading.tsx` | Never a bare "Loading…" text. Waiting content gets a skeleton in its own shape (`LoadingList`, `LoadingTable`, `LoadingDetail`, `LoadingCards`, `LoadingForm`; the calendar week, the to-do board and the record table draw their own), a `role="status"` named "Loading …" with fixed (not random) widths. A thin `bg-primary` bar slides along the top bar's bottom edge while a query loads for the first time (after 150 ms, never for background refreshes or polling); a route change shows `LoadingPage` inside the shell. Buttons that wait show `AppButton loading`. |
| Selects | `ui/native-select.tsx` | Every select is a `NativeSelect`: a native `<select>` (keyboard, mobile pickers and tests stay native) in the `FIELD` look with a muted `ChevronDown` instead of the browser arrow. `size="sm"` for table filters (same height, `text-xs`), `variant="ghost"` between toolbar buttons. Where the browser supports `appearance: base-select` (Chromium), `globals.css` styles the opened list like a dropdown menu: popover colors, hairline border, `--shadow-popover`, rows with an accent hover and a check on the chosen one; elsewhere the system list stays. Never a bare `<select>`. |
| Search bar | `layout/CommandPalette.tsx` | The top bar's one search: an `h-9` field (`FIELD` look) up to `max-w-xl` in the middle, the shortcut as a small `kbd` on the right. It is the command input itself; its list opens directly underneath (same width, `bg-popover`, hairline border, `shadow-md`), never as a centered dialog. |
| List rows | Recent activity, Today, mailboxes | `rounded-md px-2 py-1.5–2.5` + `ROW_HOVER`; the list is `-mx-2` so the hover background bleeds to the column edge while text stays aligned with the heading. Avatars at 24px. |
| Tabs | Recent activity | Owned shadcn `Tabs` with `variant="line"`, `text-xs` triggers. Each tab mounts its own query. |
| Link lists | `home/NavigateLinks.tsx` | Icon + title + one-line description. Hover: `bg-muted`, icon turns `text-foreground`, title underlines (`group-hover:underline`). |
| Page header | `app/PageHeader.tsx` | Every page except Home. Row 1: `PageTrail` breadcrumbs (`Home › Collections › People › current`, `text-xs` muted links, current page `aria-current`). Row 2 (Azure style): bare page icon, `h1` title, optional light `| Subtitle`, then the page's command bar after a hairline, and a ghost **Close** ✕ at the far right that goes to the last crumb. Row 3: one-line `text-sm` muted description. Pages pass `trail` for levels between Home and themselves (`COLLECTIONS_CRUMB`, …). Optional `media` replaces the icon with a 32px round picture or tinted initial (records). Pages with their own header (reports) render `PageTrail` alone, with Close at its end. |
| Record profile | `collections/RecordDetail.tsx` | The one page with a picture: `PageTrail` (Close at its end), then the 64px picture or tinted initial next to `PageTitle` (title \| collection), "Added by … on …" and the command bar, tight under the breadcrumbs and above the tabs. No banner. Records of the calendar and to-dos lead back through those pages (`collectionTrail`). |
| Command bar | `app/PageCommands.tsx` (server-safe: `PageCommands`, `CommandLink`), `app/PageCommandButtons.tsx` (client: `RefreshCommand`, `SyncMailCommand`, `SettingsCommand`, `CollectionTableCommand`) | `role="toolbar"` row of ghost `sm` buttons, muted icon + label (icon only below `md`, label stays the accessible name). 2–4 commands per page, using only what exists: Refresh, related pages, settings sections, the system collection's table view, mail sync. Every page gets one, so the title row is never empty. |
| Data table | `collections/CollectionsOverview.tsx` | A plain `<table>` without a frame: `text-xs` muted header row over a hairline, `border-b` between rows, `ROW_HOVER`, the name as a link stretched over the row, numbers right-aligned `tabular-nums`, secondary columns `hidden md:table-cell`. Above it a search field, `FilterChip`s with counts and a muted result count on the right. |
| Record table | `collections/CollectionTable.tsx` | The dense variant for a collection's records: `h-9` sentence-case header, rows `py-3.5` (48px), hairlines between rows and between the data columns, values as plain text (choice values too, no tints), the open button only on hover/focus. The toolbar (search left; Columns, Reports, Filters right) holds everything else, so the table gets the page: it fills the height down to the record count (`flex-1 min-h-0`) and scrolls on its own with a sticky header. |
| Calendar | `calendar/CalendarView.tsx`, `lib/calendar-week.ts` | *Week* or *Month*, chosen with a segmented radio group next to the arrows (like the theme setting) and kept in the URL together with the period shown (`?view=week&date=2026-10-19`, the week's Monday; `?view=month&date=2026-10-01`; any day inside the period works in a link; no `date` = around today, which *Today* sets, and plain `/calendar` is this week), so every week and month can be linked to and survives a reload; every switch and step is a history entry, so Back and Forward move between the views and periods looked at; switching keeps the opened day (the week or month around it is shown, the day stays selected). From `lg` the calendar is exactly the rest of the screen: the page does not scroll, the week grid or month grid fills its column (the week scrolls its hours inside), and the agenda scrolls in its own column under a sticky heading. The month: six full weeks in one framed grid (every month the same height), hairlines between the cells, weekday names on top, the neighbouring months' days greyed out (`bg-muted/60`, muted text, events at 60%), today as the `bg-primary` pill; every event of the day in its cell (all-day ones as a tinted bar first, timed ones as a tinted dot + the full title wrapped between words, the time only from `xl`), no "N more": a cell whose events do not fit scrolls them on its own, with a scrollbar that only shows while the pointer is over the cell; the cell (and the empty space under its events) opens the day. Neither the week nor the month ever scrolls sideways: the seven columns always share the width (on phones the week header stacks weekday over date). Below, the week: one framed grid (it is one object) with a 3.5rem hour gutter and seven day columns divided by hairlines, 48px per hour, scrolling its hours on its own (never sideways) with a sticky day header; it opens at the working day or a little before now. Today's date is a small `bg-primary` pill (`aria-current="date"`); weekends sit on `bg-muted/30`. Events are the only color: `bg-tint-1` blocks with a `border-tint-1-vivid` left edge (the calendar's tint), title + time range, placed by start and end (an hour without an end), overlapping ones side by side, past ones at 60% opacity; all-day events in a row above the hours. The current time is a 2px `bg-primary` line with a dot in today's column. Beside it (20rem, sticky) the *Agenda*: a status dot line (overdue or "All to-dos are on time"), an *Overdue* group, then one group per day with something on it under a small `bg-muted/50` day bar; events with a muted calendar icon and time range, to-dos with a round button that marks them done and their due label. A day header, month cell or hour slot swaps the agenda for that day with the quick-add form; that day stays marked as selected (week: `bg-accent` header with a 2px `--ring` line along its bottom; month: `bg-accent` cell with an inset ring). Pressing an hour and dragging up or down over others plans an event over all of them (a click plans one hour): the hours show as a dashed "New event" block with the range while dragging and while the form is open, and the form gets *Time* and *Until* filled in (saved as `starts_at`/`ends_at`). Text never gets cut mid-line: a week block gives its title as many lines as its height leaves next to the time line; month chips wrap to two lines; agenda titles and places wrap in full. |
| Kind label | `app/KindLabel.tsx` | What a card or sheet shows (proposal kind, *Insight*, *Reply suggestion*): a 14px bare icon in its vivid tint next to the muted `text-xs` label, importance badge after it. Never an icon on a colored tile; the reasoning under a proposal is plain muted text, not a gray box. |
| Record sheet | `collections/RecordSheet.tsx`, `RecordDetail variant="sheet"` | A record's detail page as the right sidebar (calendar events open in it): `DetailSheet` with the collection as label (section icon in its tint) and the record title, a ghost *Open full screen* icon button (Maximize2, links to the record page) in `DetailSheet`'s `actions` slot, pinned next to the close button with the same variant, size and row. Inside, the page's own content minus what belongs to a page: a 40px picture with "Added by …", then the same tabs (labels without icons, closer together, so all four fit a third of the window) and the same edit form. Links that open a record in it keep their `href`: a plain click opens the sheet, Ctrl/⌘/Shift/middle click the page. |
| Detail sheet | `app/DetailSheet.tsx` | The right sidebar for proposals, findings, to-dos, calendar events and collection reports: the header is a small bare icon in its color next to the label (no tinted tile, like the page headers) over the title; opens at a third of the window (at least 24rem; full width on phones) over the page, which is neither dimmed nor blurred (clear overlay; a click outside closes it). Its left edge is a resize handle (`role=separator`: drag, arrow keys, double click resets; 20rem to 90% of the window), the width a per-browser preference (`contor.detailSheet.width`). |
| Side work in a sheet | `reports/CollectionReports.tsx` + `DetailSheet` | Secondary work that would push the main content down (asking for a report) lives behind a toolbar button with a muted count and opens in the right sheet; lists inside use plain rows (`reports/ReportRow.tsx`, shared with the record's report list). |
| Workspace (list + detail) | `reports/RecordReports.tsx` | Takes the whole page: full width and, on large screens, exactly the rest of the screen below the tab bar (the record page becomes a flex column whose tab panel takes the remaining height; no page scroll, no `calc(100dvh…)` guess). A closable side list (16rem, own scroll; it slides in and out: the grid column animates 16rem ↔ 0 over 200ms, content at a fixed width so it does not reflow, `inert` while closed) with a full-height `lg:border-r` divider, a `text-sm font-medium` title, New and Close icon buttons, a `SearchField` and plain rows (current one `bg-accent font-medium`); next to it the detail (an embedded page component with an *Open full screen* `CommandLink`, scrolling on its own and never sideways) or the start state centered in its area (question, input, suggestion buttons). The list closes from its own header (PanelLeftClose, next to New); while it is closed an open button (PanelLeftOpen, fades in) appears in a fixed `h-8` row at the top of the detail. The row is there in both states, so opening or closing never moves the content up or down. Open/closed is a per-browser preference (`useStoredValue`). |
| Edit form | `collections/RecordDetailsForm.tsx`, `RecordFieldInput` | Data is edited in place, no separate view mode: every value is an input in the shared field look (selects too), labelled above, in a 1/2/3-column grid (long text full width). A link-like value (email, phone, URL) gets a small ghost open button beside its input; the wrapper stays the same with or without it, so typing never loses focus. Save appears only after a change, in a sticky bottom bar with a muted change count, *Discard* (ghost) and *Save*; it sends only the changed values; errors show inline (`role="alert"`), success as a toast. |
| Navigation drawer | `layout/NavDrawer.tsx` | Opened by the first button of the top bar (☰). `h-8` rows inset with `mx-2 rounded-md px-2`, `gap-px` between them. Colorful but minimal: only the icon carries color (section tint, collection tint via `tintVividClass`), never a tile or row background; current page `bg-accent font-medium`; counts as gray `secondary` badges; group labels `text-xs` muted; the search is a `SearchField`. |
| Fields | `ui/input.tsx` (`FIELD`, `FIELD_FOCUS`, `FIELD_FOCUS_WITHIN`), `ui/textarea.tsx`, `app/SearchField.tsx` | One look for every text field: `h-8 rounded-md border border-input bg-background text-sm`, focus `border-ring ring-2 ring-ring/25`. Search boxes are always `SearchField` (icon at `left-2.5`, `pl-8`, our own Clear ✕, optional trailing controls). Boxes that hold a borderless control (report composer, address chips) use `FIELD` + `FIELD_FOCUS_WITHIN`. No per-use heights or borders; `components/fields.test.ts` fails on any raw text `<input>`/`<textarea>` outside `components/ui`. |
| Top bar | `layout/TopBar.tsx` | 3rem, `bg-background border-b`: menu (☰) first, then app launcher and logo (a `size-6` black square with a white "c"), the mail `SearchField`, ghost icon buttons. |

Buttons: `default` (black) only for the single primary action of a row ("Approve"). Use `outline` for secondary actions
and `ghost` for tertiary ones and icon buttons. Inside lists use `size="xs"`, elsewhere `sm`.

## 7. States

- **Loading:** skeletons in the final shape with deterministic widths (`w-3/4`, `w-2/3`, …, never random). Numbers get
  a `h-7 w-12` block, rows get avatar + line skeletons.
- **Empty:** `QuietEmpty` with a reason and, if useful, the next step ("Go to inbox"). Never a blank area.
- **Empty detail pane** (nothing selected yet): a framed placeholder, `rounded-lg border-dashed bg-muted/30`, aligned with the top of the list beside it, never floating text in a tall empty area (`mail/ReadingPanePlaceholder.tsx`). The pane only takes the screen height once something is open.
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
- Visually hidden headings (`sr-only`) are fine where a visible one would be noise.

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

1. `PageHeader` with the right `trail` (Close must land one level up) and a `PageCommands` bar, plain page background, `max-w-[85rem]` container, `gap-8` rhythm.
2. Sections are `HomeSection`-style headings over a hairline. Frame something only if it is one object.
3. Color only for status marks; weight only for titles and unread.
4. Lists: `-mx-2` + `ROW_HOVER` rows, `Pager` when they can grow, `QuietEmpty` when empty, shaped skeletons while loading.
5. Grids with `minmax(0,1fr)` tracks; check 1920px and 390px in light and dark (`npm run screenshots`).
6. Grep the diff for raw colors before committing.
