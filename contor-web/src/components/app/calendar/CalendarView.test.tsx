import { act, configure, fireEvent, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { mockApi } from "@/test/api-mock";
import { followNavigation, navigationState, resetNavigation, router } from "@/test/next-navigation";
import { renderWithProviders } from "@/test/render";
import { CalendarView, HOUR_HEIGHT } from "./CalendarView";

vi.mock("next/navigation", () => import("@/test/next-navigation"));

// The week grid (168 hour slots) and the agenda take a while to render in jsdom; under a full parallel run the
// default 1s for findBy/waitFor was sometimes not enough. Applies to this file only (each test file is isolated).
configure({ asyncUtilTimeout: 3000 });

const collections = [
  { id: 3, slug: "events", name: "Calendar", system: true, matchKeys: [], displayConfig: {}, schemaVersion: 1, createdBy: "USER", fields: [] },
  { id: 4, slug: "tasks", name: "To-dos", system: true, matchKeys: [], displayConfig: {}, schemaVersion: 1, createdBy: "USER", fields: [] },
];

function record(id: number, collectionId: number, title: string, values: Record<string, unknown>) {
  return { id, collectionId, title, values, extra: {}, createdAt: "2026-10-01T00:00:00Z", createdBy: "AI" };
}

function page(items: unknown[]) {
  return { items, page: 0, size: 200, totalElements: items.length, totalPages: 1 };
}

// Local times, so the tests do not depend on the machine's time zone.
const events = page([
  record(1, 3, "Interview at Acme", {
    starts_at: new Date(2026, 9, 9, 10, 0).toISOString(),
    ends_at: new Date(2026, 9, 9, 11, 0).toISOString(),
    location: "Acme HQ",
    all_day: false,
  }),
  record(2, 3, "Company holiday", { starts_at: new Date(2026, 9, 7).toISOString(), all_day: true }),
]);

const tasks = page([
  record(11, 4, "Send invoice", { state: "open", due_date: "2026-10-09" }),
  record(12, 4, "Call Acme", { state: "in_progress", due_date: "2026-10-01" }),
  record(13, 4, "Already done", { state: "done", due_date: "2026-10-09" }),
]);

function api(extra: Parameters<typeof mockApi>[0] = {}) {
  return mockApi({
    "GET /api/collections": { body: collections },
    "GET /api/collections/3/records": { body: events },
    "GET /api/collections/4/records": { body: tasks },
    ...extra,
  });
}

describe("CalendarView", () => {
  beforeEach(() => {
    resetNavigation("/calendar");
    followNavigation();
    vi.useFakeTimers({ toFake: ["Date"] });
    // Thursday noon.
    vi.setSystemTime(new Date(2026, 9, 8, 12, 0));
  });

  afterEach(() => {
    vi.useRealTimers();
    window.localStorage.clear();
  });

  it("switches to a month view (?view=month), loading the whole six-week grid", async () => {
    const urls: URL[] = [];
    api({
      "GET /api/collections/3/records": ({ url }) => {
        urls.push(url);
        return { body: events };
      },
    });
    renderWithProviders(<CalendarView />);
    await screen.findByRole("heading", { name: "5 – 11 October 2026" });

    await userEvent.click(screen.getByRole("radio", { name: "Month" }));

    expect(screen.getByRole("heading", { name: "October 2026" })).toBeInTheDocument();
    const friday = await screen.findByRole("listitem", { name: "Friday, 9 October 2026" });
    expect(await within(friday).findByRole("link", { name: /Interview at Acme/ })).toHaveAttribute("href", "/calendar?view=month&date=2026-10-01&record=3-1");
    expect(within(screen.getByRole("listitem", { name: "Thursday, 8 October 2026" })).getByText("8")).toHaveAttribute("aria-current", "date");
    // Days of the neighbouring months fill the grid, muted.
    expect(screen.getByRole("listitem", { name: "Monday, 28 September 2026" })).toHaveClass("text-muted-foreground");
    await waitFor(() =>
      expect(urls.at(-1)?.searchParams.getAll("filter")).toEqual([
        `starts_at:gte:${new Date(2026, 8, 28).toISOString()}`,
        `starts_at:lt:${new Date(2026, 10, 9).toISOString()}`,
      ]),
    );
    // The view lives in the URL, so it can be linked to and survives a reload.
    expect(router.push).toHaveBeenLastCalledWith("/calendar?view=month&date=2026-10-01", { scroll: false });
  });

  it("opens the week of the date picked in the month, with that date still selected, and back", async () => {
    api();
    navigationState.search = "view=month";
    renderWithProviders(<CalendarView />);
    await screen.findByRole("heading", { name: "October 2026" });

    // A day of the next month, shown at the end of October's grid: the month stays.
    await userEvent.click(screen.getByRole("button", { name: "Open Monday, 2 November" }));
    expect(screen.getByRole("heading", { name: "October 2026" })).toBeInTheDocument();

    await userEvent.click(screen.getByRole("radio", { name: "Week" }));
    expect(screen.getByRole("heading", { name: "2 – 8 November 2026" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Open Monday, 2 November" })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("region", { name: "Monday, 2 November" })).toBeInTheDocument();

    await userEvent.click(screen.getByRole("radio", { name: "Month" }));
    expect(screen.getByRole("heading", { name: "November 2026" })).toBeInTheDocument();
  });

  it("greys out the days of the neighbouring months", async () => {
    api();
    navigationState.search = "view=month";
    renderWithProviders(<CalendarView />);

    const september = await screen.findByRole("listitem", { name: "Monday, 28 September 2026" });
    expect(september).toHaveClass("bg-muted/60", "text-muted-foreground");
    expect(screen.getByRole("listitem", { name: "Thursday, 1 October 2026" })).not.toHaveClass("bg-muted/60");
  });

  it("fits the page on large screens: the grid takes the rest of the height, the agenda scrolls on its own", async () => {
    api();
    renderWithProviders(<CalendarView />);

    const agenda = await screen.findByRole("region", { name: "Agenda" });
    expect(agenda).toHaveClass("lg:min-h-0", "lg:overflow-y-auto");
    const layout = agenda.parentElement as HTMLElement;
    expect(layout).toHaveClass("lg:min-h-0", "lg:flex-1", "lg:grid-rows-[minmax(0,1fr)]");
    expect(screen.getByTestId("week-scroller")).toHaveClass("lg:min-h-0", "lg:flex-1");
  });

  it("opens the view named in the URL and keeps the other parameters when switching", async () => {
    api();
    navigationState.search = "view=month&settings=general";
    renderWithProviders(<CalendarView />);

    expect(await screen.findByRole("heading", { name: "October 2026" })).toBeInTheDocument();
    expect(screen.getByRole("radio", { name: "Month" })).toBeChecked();

    await userEvent.click(screen.getByRole("radio", { name: "Week" }));
    expect(router.push).toHaveBeenLastCalledWith("/calendar?view=week&settings=general&date=2026-10-05", { scroll: false });
    expect(screen.getByRole("heading", { name: "5 – 11 October 2026" })).toBeInTheDocument();
  });

  it("opens the week or month named by ?date= (any day inside it)", async () => {
    api();
    navigationState.search = "view=week&date=2026-11-18";
    renderWithProviders(<CalendarView />);
    expect(await screen.findByRole("heading", { name: "16 – 22 November 2026" })).toBeInTheDocument();

    // Following a link to another period (the URL changes while the calendar stays open).
    act(() => router.push("/calendar?view=month&date=2026-12-15"));
    expect(await screen.findByRole("heading", { name: "December 2026" })).toBeInTheDocument();
  });

  it("falls back to this week for an unknown view or date", async () => {
    api();
    navigationState.search = "view=year&date=2026-13-45";
    renderWithProviders(<CalendarView />);

    expect(await screen.findByRole("heading", { name: "5 – 11 October 2026" })).toBeInTheDocument();
  });

  it("moves between months and back to the week of today", async () => {
    api();
    navigationState.search = "view=month";
    renderWithProviders(<CalendarView />);

    expect(await screen.findByRole("heading", { name: "October 2026" })).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Next month" }));
    expect(screen.getByRole("heading", { name: "November 2026" })).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Today" }));
    expect(screen.getByRole("heading", { name: "October 2026" })).toBeInTheDocument();
    await userEvent.click(screen.getByRole("radio", { name: "Week" }));
    expect(screen.getByRole("heading", { name: "5 – 11 October 2026" })).toBeInTheDocument();
  });

  it("lists every event of a day in its cell, which scrolls on its own instead of saying how many more", async () => {
    const busy = page(
      [1, 2, 3, 4, 5].map((hour) =>
        record(100 + hour, 3, `Meeting ${hour}`, { starts_at: new Date(2026, 9, 20, 8 + hour).toISOString(), all_day: false }),
      ),
    );
    api({ "GET /api/collections/3/records": { body: busy } });
    navigationState.search = "view=month";
    renderWithProviders(<CalendarView />);

    const tuesday = await screen.findByRole("listitem", { name: "Tuesday, 20 October 2026" });
    expect(await within(tuesday).findAllByRole("link")).toHaveLength(5);
    expect(within(tuesday).getByRole("link", { name: /Meeting 1/ })).toHaveAttribute("title", "Meeting 1 · 09:00 – 10:00");
    expect(within(tuesday).queryByRole("button", { name: /more on/ })).not.toBeInTheDocument();
    const list = within(tuesday).getByTestId("cell-events");
    expect(list).toHaveClass("overflow-y-auto", "overflow-x-hidden");
    // A barely-there scrollbar (globals.css); an inline scrollbar-color would make Chromium draw its wide native one.
    expect(list).toHaveClass("scrollbar-subtle");
    expect(list.className).not.toMatch(/scrollbar-color/);
  });

  it("puts a day's events beside its date in the month (below it only on phones)", async () => {
    api();
    navigationState.search = "view=month";
    renderWithProviders(<CalendarView />);

    const friday = await screen.findByRole("listitem", { name: "Friday, 9 October 2026" });
    const list = await within(friday).findByTestId("cell-events");
    const date = within(friday).getByText("9");
    expect(friday).toHaveClass("flex-col", "sm:flex-row");
    expect(list.parentElement).toBe(date.parentElement);
    expect(list).toHaveClass("flex-1", "min-w-0");
  });

  it("never scrolls sideways: week and month fit their width", async () => {
    api();
    renderWithProviders(<CalendarView />);

    const week = await screen.findByTestId("week-scroller");
    expect(week).toHaveClass("overflow-x-hidden");
    expect(week.firstElementChild?.className).not.toMatch(/min-w-\[/);

    await userEvent.click(screen.getByRole("radio", { name: "Month" }));
    const month = screen.getByTestId("month-grid");
    expect(month).toHaveClass("overflow-x-hidden");
    expect(month.innerHTML).not.toMatch(/min-w-\[/);
  });

  it("shows the current week with the events at their time, as links that open them in the detail sidebar", async () => {
    const urls: URL[] = [];
    api({
      "GET /api/collections/3/records": ({ url }) => {
        urls.push(url);
        return { body: events };
      },
    });
    renderWithProviders(<CalendarView />);

    expect(await screen.findByRole("heading", { name: "5 – 11 October 2026" })).toBeInTheDocument();
    const friday = screen.getByRole("group", { name: "Friday, 9 October 2026" });
    const interview = await within(friday).findByRole("link", { name: /Interview at Acme/ });
    expect(interview).toHaveAttribute("href", "/calendar?record=3-1");
    expect(interview).toHaveStyle({ top: `${10 * HOUR_HEIGHT}px`, height: `${HOUR_HEIGHT}px` });
    expect(interview).toHaveTextContent("10:00 – 11:00");

    expect(urls[0]?.searchParams.getAll("filter")).toEqual([
      `starts_at:gte:${new Date(2026, 9, 5).toISOString()}`,
      `starts_at:lt:${new Date(2026, 9, 12).toISOString()}`,
    ]);
  });

  it("zooms the hours with Ctrl and the mouse wheel, remembered, within limits", async () => {
    api();
    renderWithProviders(<CalendarView />);
    const friday = await screen.findByRole("group", { name: "Friday, 9 October 2026" });
    const interview = await within(friday).findByRole("link", { name: /Interview at Acme/ });
    const scroller = screen.getByTestId("week-scroller");
    const height = () => Number.parseFloat(interview.style.height);
    expect(height()).toBe(HOUR_HEIGHT);

    // A plain wheel scrolls as usual; the page is not zoomed.
    expect(fireEvent.wheel(scroller, { deltaY: -100 })).toBe(true);
    expect(height()).toBe(HOUR_HEIGHT);

    // Ctrl + wheel down: smaller hours (the browser's own zoom is prevented).
    expect(fireEvent.wheel(scroller, { deltaY: 100, ctrlKey: true })).toBe(false);
    const smaller = height();
    expect(smaller).toBeLessThan(HOUR_HEIGHT);
    expect(interview).toHaveStyle({ top: `${10 * smaller}px` });
    expect(Number(window.localStorage.getItem("contor.calendar.hourHeight"))).toBe(smaller);

    // Ctrl + wheel up: bigger again; never below or above the limits.
    fireEvent.wheel(scroller, { deltaY: -100, ctrlKey: true });
    expect(height()).toBeGreaterThan(smaller);
    for (let step = 0; step < 40; step++) fireEvent.wheel(scroller, { deltaY: 100, ctrlKey: true });
    expect(height()).toBe(16);
    for (let step = 0; step < 40; step++) fireEvent.wheel(scroller, { deltaY: -100, ctrlKey: true });
    expect(height()).toBe(160);
  });

  it("opens the week at the remembered zoom", async () => {
    window.localStorage.setItem("contor.calendar.hourHeight", "24");
    api();
    renderWithProviders(<CalendarView />);
    const friday = await screen.findByRole("group", { name: "Friday, 9 October 2026" });

    expect(await within(friday).findByRole("link", { name: /Interview at Acme/ })).toHaveStyle({ top: "240px", height: "24px" });
  });

  it("shows all-day events above the hours", async () => {
    api();
    renderWithProviders(<CalendarView />);

    const wednesday = await screen.findByRole("group", { name: "Wednesday, 7 October 2026" });
    const holiday = await within(wednesday).findByRole("link", { name: /Company holiday/ });
    expect(holiday.closest("[data-all-day]")).not.toBeNull();
  });

  it("marks today and draws a line at the current time", async () => {
    api();
    renderWithProviders(<CalendarView />);

    const thursday = await screen.findByRole("group", { name: "Thursday, 8 October 2026" });
    expect(within(thursday).getByText("8")).toHaveAttribute("aria-current", "date");
    expect(within(thursday).getByTestId("now-line")).toHaveStyle({ top: `${12 * HOUR_HEIGHT}px` });
    expect(within(screen.getByRole("group", { name: "Friday, 9 October 2026" })).queryByTestId("now-line")).toBeNull();
  });

  it("moves between weeks, each one its own address", async () => {
    api();
    renderWithProviders(<CalendarView />);
    await screen.findByRole("heading", { name: "5 – 11 October 2026" });

    await userEvent.click(screen.getByRole("button", { name: "Next week" }));
    expect(screen.getByRole("heading", { name: "12 – 18 October 2026" })).toBeInTheDocument();
    expect(router.push).toHaveBeenLastCalledWith("/calendar?view=week&date=2026-10-12", { scroll: false });
    await userEvent.click(screen.getByRole("button", { name: "Previous week" }));
    await userEvent.click(screen.getByRole("button", { name: "Previous week" }));
    expect(screen.getByRole("heading", { name: "28 September – 4 October 2026" })).toBeInTheDocument();
    expect(router.push).toHaveBeenLastCalledWith("/calendar?view=week&date=2026-09-28", { scroll: false });
    // Today needs no date: the address stays valid tomorrow.
    await userEvent.click(screen.getByRole("button", { name: "Today" }));
    expect(screen.getByRole("heading", { name: "5 – 11 October 2026" })).toBeInTheDocument();
    expect(router.push).toHaveBeenLastCalledWith("/calendar?view=week", { scroll: false });
  });

  it("lists the week's events and open to-dos by day, overdue ones first", async () => {
    api();
    renderWithProviders(<CalendarView />);

    const agenda = await screen.findByRole("region", { name: "Agenda" });
    expect(await within(agenda).findByText("1 to-do overdue")).toBeInTheDocument();
    const overdue = within(agenda).getByRole("group", { name: "Overdue" });
    expect(within(overdue).getByText("Call Acme")).toBeInTheDocument();
    expect(within(overdue).getByText("Overdue by 7 days")).toBeInTheDocument();

    const friday = within(agenda).getByRole("group", { name: "Friday, 9 October" });
    expect(within(friday).getByRole("link", { name: /Interview at Acme/ })).toHaveTextContent("10:00 – 11:00");
    expect(within(friday).getByText("Send invoice")).toBeInTheDocument();
    expect(within(agenda).getByRole("group", { name: "Wednesday, 7 October" })).toHaveTextContent("All day");
    expect(within(agenda).queryByText("Already done")).not.toBeInTheDocument();
    // Days without anything are left out.
    expect(within(agenda).queryByRole("group", { name: "Monday, 5 October" })).not.toBeInTheDocument();
  });

  it("shows the real end of an event that ends on another day", async () => {
    api({
      "GET /api/collections/3/records": {
        body: page([
          record(7, 3, "Night shift", {
            starts_at: new Date(2026, 9, 9, 22, 0).toISOString(),
            ends_at: new Date(2026, 9, 10, 2, 0).toISOString(),
            all_day: false,
          }),
        ]),
      },
    });
    renderWithProviders(<CalendarView />);

    const agenda = await screen.findByRole("region", { name: "Agenda" });
    expect(await within(agenda).findByRole("link", { name: /Night shift/ })).toHaveTextContent("22:00 – 10 Oct 02:00");
  });

  it("opens events and to-dos in the detail sidebar (an address, so a new tab or a copied link shows it too)", async () => {
    api();
    navigationState.search = "view=week&date=2026-10-05";
    renderWithProviders(<CalendarView />);

    const sheetHref = "/calendar?view=week&date=2026-10-05&record=3-1";
    const friday = await screen.findByRole("group", { name: "Friday, 9 October 2026" });
    expect(await within(friday).findByRole("link", { name: /Interview at Acme/ })).toHaveAttribute("href", sheetHref);
    const agenda = screen.getByRole("region", { name: "Agenda" });
    expect(within(agenda).getByRole("link", { name: /Interview at Acme/ })).toHaveAttribute("href", sheetHref);
    expect(within(agenda).getByRole("link", { name: "Send invoice" })).toHaveAttribute("href", "/calendar?view=week&date=2026-10-05&record=4-11");
  });

  it("marks a to-do done from the agenda", async () => {
    const calls = api({ "PUT /api/collections/4/records/11": { body: tasks.items[0] } });
    renderWithProviders(<CalendarView />);

    await userEvent.click(await screen.findByRole("button", { name: "Mark Send invoice done" }));

    await waitFor(() => expect(calls.find((call) => call.method === "PUT")?.body).toEqual({ values: { state: "done" } }));
  });

  it("opens a day from an hour slot with that hour filled in and adds an event", async () => {
    const calls = api({ "POST /api/collections/3/records": { status: 201, body: events.items[0] } });
    renderWithProviders(<CalendarView />);
    await screen.findByRole("group", { name: "Tuesday, 6 October 2026" });

    await userEvent.click(screen.getByRole("button", { name: "New event on Tuesday, 6 October at 14:00" }));
    const day = screen.getByRole("region", { name: "Tuesday, 6 October" });
    expect(within(day).getByLabelText("Time")).toHaveValue("14:00");
    expect(within(day).getByLabelText("Until")).toHaveValue("15:00");
    await userEvent.type(within(day).getByRole("textbox", { name: "Event title" }), "Dentist");
    await userEvent.click(within(day).getByRole("button", { name: "Add event" }));

    await waitFor(() =>
      expect(calls.find((call) => call.method === "POST")?.body).toEqual({
        title: "Dentist",
        values: {
          starts_at: new Date(2026, 9, 6, 14, 0).toISOString(),
          ends_at: new Date(2026, 9, 6, 15, 0).toISOString(),
          all_day: false,
        },
      }),
    );
  });

  it("shows times with AM/PM when the 12-hour format is chosen, and still adds the event at that time", async () => {
    window.localStorage.setItem("contor.timeFormat", "\"12h\"");
    const calls = api({ "POST /api/collections/3/records": { status: 201, body: events.items[0] } });
    renderWithProviders(<CalendarView />);
    const friday = await screen.findByRole("group", { name: "Friday, 9 October 2026" });
    expect(await within(friday).findByRole("link", { name: /Interview at Acme/ })).toHaveTextContent("10:00 AM – 11:00 AM");

    await userEvent.click(screen.getByRole("button", { name: "New event on Tuesday, 6 October at 2:00 PM" }));
    const day = screen.getByRole("region", { name: "Tuesday, 6 October" });
    expect(within(day).getByLabelText("Time")).toHaveValue("2:00 PM");
    expect(within(day).getByLabelText("Until")).toHaveValue("3:00 PM");
    await userEvent.clear(within(day).getByLabelText("Until"));
    await userEvent.type(within(day).getByLabelText("Until"), "4:30 pm");
    await userEvent.type(within(day).getByRole("textbox", { name: "Event title" }), "Dentist");
    await userEvent.click(within(day).getByRole("button", { name: "Add event" }));

    await waitFor(() =>
      expect(calls.find((call) => call.method === "POST")?.body).toEqual({
        title: "Dentist",
        values: {
          starts_at: new Date(2026, 9, 6, 14, 0).toISOString(),
          ends_at: new Date(2026, 9, 6, 16, 30).toISOString(),
          all_day: false,
        },
      }),
    );
  });

  it("drags across hours to plan an event over all of them, shown in the grid while it is planned", async () => {
    const calls = api({ "POST /api/collections/3/records": { status: 201, body: events.items[0] } });
    renderWithProviders(<CalendarView />);
    const tuesday = await screen.findByRole("group", { name: "Tuesday, 6 October 2026" });
    const slot = (time: string) => screen.getByRole("button", { name: `New event on Tuesday, 6 October at ${time}` });

    fireEvent.pointerDown(slot("14:00"));
    fireEvent.pointerEnter(slot("15:00"));
    fireEvent.pointerEnter(slot("16:00"));
    // While dragging, the range shows in the column.
    expect(within(tuesday).getByTestId("planned-event")).toHaveStyle({ top: `${14 * HOUR_HEIGHT}px`, height: `${3 * HOUR_HEIGHT}px` });
    fireEvent.pointerUp(window);

    const day = screen.getByRole("region", { name: "Tuesday, 6 October" });
    expect(within(day).getByLabelText("Time")).toHaveValue("14:00");
    expect(within(day).getByLabelText("Until")).toHaveValue("17:00");
    expect(within(tuesday).getByTestId("planned-event")).toHaveTextContent("14:00 – 17:00");
    await userEvent.type(within(day).getByRole("textbox", { name: "Event title" }), "Workshop");
    await userEvent.click(within(day).getByRole("button", { name: "Add event" }));

    await waitFor(() =>
      expect(calls.find((call) => call.method === "POST")?.body).toEqual({
        title: "Workshop",
        values: {
          starts_at: new Date(2026, 9, 6, 14, 0).toISOString(),
          ends_at: new Date(2026, 9, 6, 17, 0).toISOString(),
          all_day: false,
        },
      }),
    );
  });

  it("drags upwards too", async () => {
    api();
    renderWithProviders(<CalendarView />);
    await screen.findByRole("group", { name: "Tuesday, 6 October 2026" });
    const slot = (time: string) => screen.getByRole("button", { name: `New event on Tuesday, 6 October at ${time}` });

    fireEvent.pointerDown(slot("11:00"));
    fireEvent.pointerEnter(slot("09:00"));
    fireEvent.pointerUp(window);

    const day = screen.getByRole("region", { name: "Tuesday, 6 October" });
    expect(within(day).getByLabelText("Time")).toHaveValue("09:00");
    expect(within(day).getByLabelText("Until")).toHaveValue("12:00");
  });

  it("marks the opened day as selected in the week and in the month", async () => {
    api();
    renderWithProviders(<CalendarView />);
    const open = await screen.findByRole("button", { name: "Open Monday, 5 October" });
    expect(open).toHaveAttribute("aria-pressed", "false");

    await userEvent.click(open);
    expect(screen.getByRole("button", { name: "Open Monday, 5 October" })).toHaveAttribute("aria-pressed", "true");

    await userEvent.click(screen.getByRole("radio", { name: "Month" }));
    expect(screen.getByRole("button", { name: "Open Monday, 5 October" })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("listitem", { name: "Monday, 5 October 2026" })).toHaveClass("bg-accent");
    await userEvent.click(screen.getByRole("button", { name: "Back to agenda" }));
    expect(screen.getByRole("button", { name: "Open Monday, 5 October" })).toHaveAttribute("aria-pressed", "false");
  });

  it("fits an event's title to its height so the time stays visible, and shows full titles in the agenda", async () => {
    api();
    renderWithProviders(<CalendarView />);

    const friday = await screen.findByRole("group", { name: "Friday, 9 October 2026" });
    const block = await within(friday).findByRole("link", { name: /Interview at Acme/ });
    // One hour has room for one title line and the time.
    expect(within(block).getByText("Interview at Acme")).toHaveStyle({ WebkitLineClamp: "1" });
    const agenda = screen.getByRole("region", { name: "Agenda" });
    expect(within(agenda).getByText("Interview at Acme")).not.toHaveClass("truncate");
  });

  it("opens a day from its header and adds an all-day event, then goes back to the agenda", async () => {
    const calls = api({ "POST /api/collections/3/records": { status: 201, body: events.items[0] } });
    renderWithProviders(<CalendarView />);
    await screen.findByRole("group", { name: "Monday, 5 October 2026" });

    await userEvent.click(screen.getByRole("button", { name: "Open Monday, 5 October" }));
    const day = screen.getByRole("region", { name: "Monday, 5 October" });
    expect(within(day).getByText("Nothing planned.")).toBeInTheDocument();
    await userEvent.type(within(day).getByRole("textbox", { name: "Event title" }), "Holiday");
    await userEvent.click(within(day).getByRole("checkbox", { name: "All day" }));
    await userEvent.click(within(day).getByRole("button", { name: "Add event" }));
    await waitFor(() =>
      expect(calls.find((call) => call.method === "POST")?.body).toEqual({
        title: "Holiday",
        values: { starts_at: new Date(2026, 9, 5).toISOString(), all_day: true },
      }),
    );

    await userEvent.click(within(day).getByRole("button", { name: "Back to agenda" }));
    expect(screen.getByRole("region", { name: "Agenda" })).toBeInTheDocument();
  });
});
