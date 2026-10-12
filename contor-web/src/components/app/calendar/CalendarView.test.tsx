import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { mockApi } from "@/test/api-mock";
import { renderWithProviders } from "@/test/render";
import { CalendarView } from "./CalendarView";

const collections = [
  { id: 3, slug: "events", name: "Calendar", system: true, matchKeys: [], displayConfig: {}, schemaVersion: 1, createdBy: "USER", fields: [] },
];

const events = {
  items: [
    {
      id: 1,
      collectionId: 3,
      title: "Interview at Acme",
      values: { starts_at: "2026-10-09T10:00:00Z", location: "Acme HQ", all_day: false },
      extra: {},
      createdAt: "2026-10-01T00:00:00Z",
      createdBy: "AI",
    },
  ],
  page: 0,
  size: 200,
  totalElements: 1,
  totalPages: 1,
};

describe("CalendarView", () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date("2026-10-08T12:00:00Z"));
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("shows the events of the current month on their day", async () => {
    const calls = mockApi({ "GET /api/collections": { body: collections }, "GET /api/collections/3/records": { body: events } });

    renderWithProviders(<CalendarView />);

    expect(await screen.findByRole("heading", { name: "October 2026" })).toBeInTheDocument();
    const day = await screen.findByRole("listitem", { name: "9 October 2026" });
    expect(await within(day).findByText("Interview at Acme")).toBeInTheDocument();
    const request = calls.find((call) => call.path === "/api/collections/3/records");
    expect(request).toBeDefined();
  });

  it("moves between months", async () => {
    mockApi({ "GET /api/collections": { body: collections }, "GET /api/collections/3/records": { body: events } });

    renderWithProviders(<CalendarView />);
    await screen.findByRole("heading", { name: "October 2026" });

    await userEvent.click(screen.getByRole("button", { name: "Next month" }));
    expect(screen.getByRole("heading", { name: "November 2026" })).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Today" }));
    await waitFor(() => expect(screen.getByRole("heading", { name: "October 2026" })).toBeInTheDocument());
  });

  it("links each event to its record", async () => {
    mockApi({ "GET /api/collections": { body: collections }, "GET /api/collections/3/records": { body: events } });

    renderWithProviders(<CalendarView />);

    const day = await screen.findByRole("listitem", { name: "9 October 2026" });
    expect(await within(day).findByRole("link", { name: /Interview at Acme/ })).toHaveAttribute("href", "/collections/3/records/1");
  });

  it("shows the agenda of a clicked day", async () => {
    mockApi({ "GET /api/collections": { body: collections }, "GET /api/collections/3/records": { body: events } });
    renderWithProviders(<CalendarView />);
    await screen.findByRole("listitem", { name: "9 October 2026" });

    await userEvent.click(screen.getByRole("button", { name: "Select 9 October 2026" }));

    const agenda = screen.getByRole("region", { name: "Friday, 9 October" });
    expect(await within(agenda).findByRole("link", { name: /Interview at Acme/ })).toHaveAttribute("href", "/collections/3/records/1");
    expect(screen.getByRole("button", { name: "Select 9 October 2026" })).toHaveAttribute("aria-pressed", "true");
  });

  it("adds an event to the selected day", async () => {
    const calls = mockApi({
      "GET /api/collections": { body: collections },
      "GET /api/collections/3/records": { body: events },
      "POST /api/collections/3/records": { status: 201, body: events.items[0] },
    });
    renderWithProviders(<CalendarView />);
    await screen.findByRole("listitem", { name: "12 October 2026" });

    await userEvent.click(screen.getByRole("button", { name: "Select 12 October 2026" }));
    const agenda = screen.getByRole("region", { name: "Monday, 12 October" });
    expect(within(agenda).getByText("Nothing planned.")).toBeInTheDocument();
    await userEvent.type(within(agenda).getByRole("textbox", { name: "Event title" }), "Dentist");
    await userEvent.type(within(agenda).getByLabelText("Time"), "14:30");
    await userEvent.click(within(agenda).getByRole("button", { name: "Add event" }));

    await waitFor(() =>
      expect(calls.find((call) => call.method === "POST")?.body).toEqual({
        title: "Dentist",
        values: { starts_at: new Date(2026, 9, 12, 14, 30).toISOString(), all_day: false },
      }),
    );
  });

  it("adds an all-day event", async () => {
    const calls = mockApi({
      "GET /api/collections": { body: collections },
      "GET /api/collections/3/records": { body: events },
      "POST /api/collections/3/records": { status: 201, body: events.items[0] },
    });
    renderWithProviders(<CalendarView />);
    await screen.findByRole("listitem", { name: "12 October 2026" });

    await userEvent.click(screen.getByRole("button", { name: "Select 12 October 2026" }));
    const agenda = screen.getByRole("region", { name: "Monday, 12 October" });
    await userEvent.type(within(agenda).getByRole("textbox", { name: "Event title" }), "Holiday");
    await userEvent.click(within(agenda).getByRole("checkbox", { name: "All day" }));
    await userEvent.click(within(agenda).getByRole("button", { name: "Add event" }));

    await waitFor(() =>
      expect(calls.find((call) => call.method === "POST")?.body).toEqual({
        title: "Holiday",
        values: { starts_at: new Date(2026, 9, 12).toISOString(), all_day: true },
      }),
    );
  });

  it("lists upcoming events", async () => {
    mockApi({ "GET /api/collections": { body: collections }, "GET /api/collections/3/records": { body: events } });

    renderWithProviders(<CalendarView />);

    const upcoming = await screen.findByRole("region", { name: "Upcoming" });
    expect(await within(upcoming).findByText("Interview at Acme")).toBeInTheDocument();
    expect(within(upcoming).getByText("Acme HQ")).toBeInTheDocument();
  });
});
