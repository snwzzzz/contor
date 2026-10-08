import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { mockApi } from "@/test/api-mock";
import { renderWithProviders } from "@/test/render";
import { TaskList } from "./TaskList";

const collections = [
  { id: 4, slug: "tasks", name: "To-dos", system: true, matchKeys: [], displayConfig: {}, schemaVersion: 1, createdBy: "USER", fields: [] },
];

function task(id: number, title: string, values: Record<string, unknown>) {
  return { id, collectionId: 4, title, values, extra: {}, createdAt: "2026-10-01T00:00:00Z", createdBy: "AI" };
}

const tasks = {
  items: [
    task(1, "Send CV to Acme", { state: "open", due_date: "2026-10-08", priority: "high", notes: "Recruiting asked for the CV and certificates before the interview." }),
    task(2, "Pay invoice 118", { state: "in_progress", due_date: null, priority: "normal", notes: null }),
    task(3, "Book flights", { state: "done", due_date: "2026-09-01", priority: "low", notes: null }),
    task(4, "Cancel subscription", { state: "open", due_date: "2026-08-01", priority: null, notes: null }),
  ],
  page: 0,
  size: 200,
  totalElements: 4,
  totalPages: 1,
};

function api(extra: Record<string, unknown> = {}) {
  return mockApi({ "GET /api/collections": { body: collections }, "GET /api/collections/4/records": { body: tasks }, ...extra });
}

describe("TaskList", () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date(2026, 9, 8, 12, 0));
  });

  afterEach(() => vi.useRealTimers());

  it("shows the to-dos side by side by state, overdue first", async () => {
    api();
    renderWithProviders(<TaskList />);

    const todo = await screen.findByRole("list", { name: "To do" });
    expect(within(todo).getAllByRole("checkbox").map((box) => box.getAttribute("aria-label"))).toEqual(["Cancel subscription", "Send CV to Acme"]);
    expect(within(screen.getByRole("list", { name: "In progress" })).getByText("Pay invoice 118")).toBeInTheDocument();
    expect(within(screen.getByRole("list", { name: "Done" })).getByRole("checkbox", { name: "Book flights" })).toBeChecked();
  });

  it("describes each to-do: deadline, priority and notes", async () => {
    api();
    renderWithProviders(<TaskList />);

    const todo = await screen.findByRole("list", { name: "To do" });
    expect(within(todo).getByText("Due today")).toBeInTheDocument();
    expect(within(todo).getByText("Overdue since 1 Aug")).toBeInTheDocument();
    expect(within(todo).getByText("High")).toBeInTheDocument();
    expect(within(todo).getByText(/Recruiting asked for the CV/)).toBeInTheDocument();
  });

  it("expands a to-do with its details and actions", async () => {
    const calls = api({ "PUT /api/collections/4/records/1": { body: task(1, "Send CV to Acme", { state: "in_progress" }) } });
    renderWithProviders(<TaskList />);

    const toggle = await screen.findByRole("button", { name: /Send CV to Acme/ });
    expect(toggle).toHaveAttribute("aria-expanded", "false");
    await userEvent.click(toggle);

    expect(toggle).toHaveAttribute("aria-expanded", "true");
    const details = screen.getByRole("region", { name: "Send CV to Acme" });
    expect(within(details).getByRole("link", { name: "Open details" })).toHaveAttribute("href", "/collections/4/records/1");
    expect(within(details).getByText(/^Added by the assistant on 1 Oct 2026/)).toBeInTheDocument();
    await userEvent.click(within(details).getByRole("button", { name: "Start" }));

    await waitFor(() => expect(calls.find((call) => call.method === "PUT")?.body).toEqual({ values: { state: "in_progress" } }));
  });

  it("checks off a to-do", async () => {
    const calls = api({ "PUT /api/collections/4/records/1": { body: task(1, "Send CV to Acme", { state: "done" }) } });
    renderWithProviders(<TaskList />);

    await userEvent.click(await screen.findByRole("checkbox", { name: "Send CV to Acme" }));

    await waitFor(() => expect(calls.find((call) => call.method === "PUT")?.body).toEqual({ values: { state: "done" } }));
  });

  it("adds a to-do", async () => {
    const calls = api({ "POST /api/collections/4/records": { status: 201, body: task(9, "Call the landlord", { state: "open" }) } });
    renderWithProviders(<TaskList />);

    await userEvent.type(await screen.findByRole("textbox", { name: "New to-do" }), "Call the landlord{Enter}");

    await waitFor(() =>
      expect(calls.find((call) => call.method === "POST")?.body).toEqual({ title: "Call the landlord", values: { state: "open" } }),
    );
  });
});
