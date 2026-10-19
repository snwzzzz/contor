import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { mockApi } from "@/test/api-mock";
import { renderWithProviders } from "@/test/render";
import { TodayCard } from "./TodayCard";
import { COLLECTIONS, NOW, page, record, todayAt } from "./test-fixtures";

describe("TodayCard", () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(NOW);
  });
  afterEach(() => vi.useRealTimers());

  it("lists today's events and the to-dos that are due", async () => {
    mockApi({
      "GET /api/collections": { body: COLLECTIONS },
      "GET /api/collections/4/records": {
        body: page([
          record(5, 4, "Standup", { starts_at: todayAt(10), all_day: false }),
          record(6, 4, "Conference", { starts_at: todayAt(0), all_day: true }),
        ]),
      },
      "GET /api/collections/3/records": {
        body: page([
          record(1, 3, "Pay invoice", { state: "open", due_date: "2026-10-17" }),
          record(2, 3, "Call Anna", { state: "open", due_date: "2026-10-19" }),
          record(3, 3, "Later", { state: "open", due_date: "2026-11-30" }),
          record(4, 3, "Finished", { state: "done", due_date: "2026-10-19" }),
        ]),
      },
    });
    renderWithProviders(<TodayCard />);

    expect(await screen.findByText("Standup")).toBeInTheDocument();
    expect(screen.getByText("10:00")).toBeInTheDocument();
    expect(screen.getByText("All day")).toBeInTheDocument();
    expect(await screen.findByText("Pay invoice")).toBeInTheDocument();
    expect(screen.getByText("Overdue by 2 days")).toBeInTheDocument();
    expect(screen.getByText("Call Anna")).toBeInTheDocument();
    expect(screen.queryByText("Later")).not.toBeInTheDocument();
    expect(screen.queryByText("Finished")).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Calendar" })).toHaveAttribute("href", "/calendar");
  });

  it("pages through a busy day", async () => {
    mockApi({
      "GET /api/collections": { body: COLLECTIONS },
      "GET /api/collections/4/records": {
        body: page([8, 9, 10, 11, 12].map((hour) => record(hour, 4, `Event at ${hour}`, { starts_at: todayAt(hour) }))),
      },
      "GET /api/collections/3/records": { body: page([]) },
    });
    renderWithProviders(<TodayCard />);

    expect(await screen.findByText("Event at 8")).toBeInTheDocument();
    expect(screen.queryByText("Event at 12")).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Next page" }));

    expect(screen.getByText("Event at 12")).toBeInTheDocument();
    expect(screen.queryByText("Event at 8")).not.toBeInTheDocument();
  });

  it("says when the day is free", async () => {
    mockApi({
      "GET /api/collections": { body: COLLECTIONS },
      "GET /api/collections/4/records": { body: page([]) },
      "GET /api/collections/3/records": { body: page([]) },
    });
    renderWithProviders(<TodayCard />);

    expect(await screen.findByText("Nothing on today")).toBeInTheDocument();
  });
});
