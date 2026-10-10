import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { mockApi, type RecordedCall } from "@/test/api-mock";
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

const transfer = () => ({ setData: vi.fn(), getData: vi.fn(), setDragImage: vi.fn(), dropEffect: "none", effectAllowed: "all" });

/** Body of the PUT to a record, if there was one. */
function put(calls: RecordedCall[], recordId: number): unknown {
  return calls.find((call) => call.method === "PUT" && call.path === `/api/collections/4/records/${recordId}`)?.body;
}

/** The API with a to-do store: PUTs change it and the next GET returns it, like the real backend. */
function api(extra: Record<string, unknown> = {}) {
  const items = tasks.items.map((item) => ({ ...item, values: { ...item.values }, extra: { ...item.extra } }));
  const update = (id: number) => ({ init }: { init: RequestInit }) => {
    const body = JSON.parse(String(init.body)) as { values?: Record<string, unknown>; extra?: Record<string, unknown> };
    const item = items.find((candidate) => candidate.id === id);
    if (!item) return { status: 404 };
    Object.assign(item.values, body.values ?? {});
    if (body.extra) item.extra = body.extra;
    return { body: item };
  };
  return mockApi({
    "GET /api/collections": { body: collections },
    "GET /api/collections/4/records": () => ({ body: { ...tasks, items } }),
    ...Object.fromEntries(items.map((item) => [`PUT /api/collections/4/records/${item.id}`, update(item.id)])),
    ...extra,
  });
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

    // Moved to the top of "In progress"; its only card had no position yet, so the column is numbered once.
    await waitFor(() => expect(put(calls, 1)).toEqual({ values: { state: "in_progress" }, extra: { boardPosition: 0 } }));
    await waitFor(() => expect(put(calls, 2)).toEqual({ extra: { boardPosition: 1024 } }));
  });

  it("drags a to-do into another column, where it lands at the pointer", async () => {
    const calls = api();
    renderWithProviders(<TaskList />);

    const card = (await screen.findByRole("checkbox", { name: "Send CV to Acme" })).closest("li") as HTMLElement;
    const done = screen.getByRole("region", { name: "Done" });
    const dataTransfer = transfer();
    fireEvent.dragStart(card, { dataTransfer });
    fireEvent.dragOver(done, { dataTransfer, clientY: 0 });

    // Above "Book flights": the preview is the whole card, deadline, priority and notes included.
    const rows = within(screen.getByRole("list", { name: "Done" })).getAllByRole("listitem", { hidden: true });
    expect(rows.map((row) => row.getAttribute("data-testid") ?? row.textContent)).toEqual(["drop-preview", expect.stringContaining("Book flights")]);
    expect(rows[0]).toHaveTextContent("Send CV to Acme");
    expect(rows[0]).toHaveTextContent("Due today");
    expect(rows[0]).toHaveTextContent("High");
    expect(rows[0]).toHaveTextContent(/Recruiting asked for the CV/);
    fireEvent.drop(done, { dataTransfer, clientY: 0 });

    // Optimistic: the card is in its new place before the server answers.
    await waitFor(() => expect(within(screen.getByRole("list", { name: "Done" })).getAllByRole("listitem")[0]).toHaveTextContent("Send CV to Acme"));
    await waitFor(() => expect(put(calls, 1)).toEqual({ values: { state: "done" }, extra: { boardPosition: 0 } }));
  });

  it("reorders the to-dos inside a column and keeps the order", async () => {
    const calls = api();
    renderWithProviders(<TaskList />);

    const card = (await screen.findByRole("checkbox", { name: "Send CV to Acme" })).closest("li") as HTMLElement;
    const todo = screen.getByRole("region", { name: "To do" });
    const dataTransfer = transfer();
    fireEvent.dragStart(card, { dataTransfer });
    fireEvent.dragOver(todo, { dataTransfer, clientY: 0 });
    fireEvent.drop(todo, { dataTransfer, clientY: 0 });

    await waitFor(() =>
      expect(within(screen.getByRole("list", { name: "To do" })).getAllByRole("checkbox").map((box) => box.getAttribute("aria-label")))
        .toEqual(["Send CV to Acme", "Cancel subscription"]),
    );
    await waitFor(() => expect(put(calls, 1)).toEqual({ extra: { boardPosition: 0 } }));
    await waitFor(() => expect(put(calls, 4)).toEqual({ extra: { boardPosition: 1024 } }));
  });

  it("orders a column by the stored positions, cards never moved first", async () => {
    mockApi({
      "GET /api/collections": { body: collections },
      "GET /api/collections/4/records": {
        body: { ...tasks, items: [
          { ...task(1, "Third", { state: "open" }), extra: { boardPosition: 2048 } },
          { ...task(2, "Second", { state: "open" }), extra: { boardPosition: 0 } },
          task(3, "New one", { state: "open" }),
        ] },
      },
    });
    renderWithProviders(<TaskList />);

    const list = await screen.findByRole("list", { name: "To do" });
    expect(within(list).getAllByRole("checkbox").map((box) => box.getAttribute("aria-label"))).toEqual(["New one", "Second", "Third"]);
  });

  it("checks off a to-do", async () => {
    const calls = api({ "PUT /api/collections/4/records/1": { body: task(1, "Send CV to Acme", { state: "done" }) } });
    renderWithProviders(<TaskList />);

    await userEvent.click(await screen.findByRole("checkbox", { name: "Send CV to Acme" }));

    await waitFor(() => expect(put(calls, 1)).toMatchObject({ values: { state: "done" } }));
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
