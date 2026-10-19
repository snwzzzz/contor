import { screen, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { mockApi } from "@/test/api-mock";
import { renderWithProviders } from "@/test/render";
import { KpiRow } from "./KpiRow";
import { COLLECTIONS, NOW, page, record, todayAt } from "./test-fixtures";

const pending = () => new Promise<never>(() => {});

describe("KpiRow", () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(NOW);
  });
  afterEach(() => vi.useRealTimers());

  it("shows four numbers at a glance, each linking to its page", async () => {
    mockApi({
      "GET /api/emails": ({ url }) => ({ body: page([], url.searchParams.get("read") === "false" ? 12 : 300) }),
      "GET /api/mail/accounts": {
        body: [
          { id: 1, provider: "MICROSOFT_GRAPH", emailAddress: "me@outlook.com", status: "ACTIVE" },
          { id: 2, provider: "MICROSOFT_GRAPH", emailAddress: "work@example.com", status: "ACTIVE" },
        ],
      },
      "GET /api/proposals/count": ({ url }) => ({ body: { count: url.searchParams.get("minImportance") === "HIGH" ? 2 : 7 } }),
      "GET /api/collections": { body: COLLECTIONS },
      "GET /api/collections/3/records": {
        body: page([
          record(1, 3, "Pay invoice", { state: "open", due_date: "2026-10-17" }),
          record(2, 3, "Call Anna", { state: "in_progress", due_date: "2026-10-25" }),
          record(3, 3, "Old", { state: "done", due_date: "2026-10-01" }),
        ]),
      },
      "GET /api/collections/4/records": {
        body: page([record(5, 4, "Standup", { starts_at: todayAt(8) }), record(6, 4, "Dentist", { starts_at: todayAt(14, 30) })]),
      },
    });
    renderWithProviders(<KpiRow />);

    const card = (name: string) => screen.getByRole("link", { name: new RegExp(name) });
    expect(await within(card("Unread mail")).findByText("12")).toBeInTheDocument();
    expect(card("Unread mail")).toHaveAttribute("href", "/inbox");
    expect(await within(card("Unread mail")).findByText("across 2 mailboxes")).toBeInTheDocument();

    expect(await within(card("Pending proposals")).findByText("7")).toBeInTheDocument();
    expect(await within(card("Pending proposals")).findByText("2 high priority")).toBeInTheDocument();
    expect(card("Pending proposals")).toHaveAttribute("href", "/proposals");

    expect(await within(card("Open to-dos")).findByText("2")).toBeInTheDocument();
    expect(within(card("Open to-dos")).getByText("1 overdue")).toBeInTheDocument();
    expect(card("Open to-dos")).toHaveAttribute("href", "/tasks");

    expect(await within(card("Today's events")).findByText("2")).toBeInTheDocument();
    expect(within(card("Today's events")).getByText("Next: 14:30 Dentist")).toBeInTheDocument();
    expect(card("Today's events")).toHaveAttribute("href", "/calendar");
  });

  it("says when no mailbox is connected instead of counting zero", async () => {
    mockApi({
      "GET /api/emails": { body: page([], 0) },
      "GET /api/mail/accounts": { body: [] },
      "GET /api/proposals/count": { body: { count: 0 } },
      "GET /api/collections": { body: [] },
    });
    renderWithProviders(<KpiRow />);

    expect(await screen.findByText("No mailbox connected")).toBeInTheDocument();
  });

  it("shows skeletons while the numbers load", () => {
    mockApi({
      "GET /api/emails": pending,
      "GET /api/mail/accounts": pending,
      "GET /api/proposals/count": pending,
      "GET /api/collections": pending,
    });
    renderWithProviders(<KpiRow />);

    for (const name of ["Unread mail", "Pending proposals", "Open to-dos", "Today's events"]) {
      expect(screen.getByRole("link", { name: new RegExp(name) }).querySelector("[data-slot=skeleton]")).not.toBeNull();
    }
  });
});
