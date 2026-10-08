import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
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
    task(1, "Send CV to Acme", { state: "open", due_date: "2026-10-08", priority: "high" }),
    task(2, "Pay invoice 118", { state: "in_progress", due_date: null, priority: "normal" }),
    task(3, "Book flights", { state: "done", due_date: "2026-09-01", priority: "low" }),
  ],
  page: 0,
  size: 200,
  totalElements: 3,
  totalPages: 1,
};

describe("TaskList", () => {
  it("lists open to-dos with due date and priority, done ones separately", async () => {
    mockApi({ "GET /api/collections": { body: collections }, "GET /api/collections/4/records": { body: tasks } });

    renderWithProviders(<TaskList />);

    const open = await screen.findByRole("list", { name: "Open to-dos" });
    expect(within(open).getAllByRole("checkbox").map((box) => box.getAttribute("aria-label"))).toEqual([
      "Send CV to Acme",
      "Pay invoice 118",
    ]);
    expect(within(open).getByText("High")).toBeInTheDocument();
    expect(within(open).getByText(/Due/)).toBeInTheDocument();
    const done = screen.getByRole("list", { name: "Done" });
    expect(within(done).getByRole("checkbox", { name: "Book flights" })).toBeChecked();
  });

  it("checks off a to-do", async () => {
    const calls = mockApi({
      "GET /api/collections": { body: collections },
      "GET /api/collections/4/records": { body: tasks },
      "PUT /api/collections/4/records/1": { body: task(1, "Send CV to Acme", { state: "done" }) },
    });

    renderWithProviders(<TaskList />);

    await userEvent.click(await screen.findByRole("checkbox", { name: "Send CV to Acme" }));

    await waitFor(() => expect(calls.find((call) => call.method === "PUT")?.body).toEqual({ values: { state: "done" } }));
  });

  it("adds a to-do", async () => {
    const calls = mockApi({
      "GET /api/collections": { body: collections },
      "GET /api/collections/4/records": { body: tasks },
      "POST /api/collections/4/records": { status: 201, body: task(9, "Call the landlord", { state: "open" }) },
    });

    renderWithProviders(<TaskList />);

    await userEvent.type(await screen.findByRole("textbox", { name: "New to-do" }), "Call the landlord{Enter}");

    await waitFor(() =>
      expect(calls.find((call) => call.method === "POST")?.body).toEqual({ title: "Call the landlord", values: { state: "open" } }),
    );
  });
});
