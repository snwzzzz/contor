import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { CollectionDto } from "@/lib/api/generated/model/collectionDto";
import type { RecordDto } from "@/lib/api/generated/model/recordDto";
import { apiError, mockApi } from "@/test/api-mock";
import { renderWithProviders } from "@/test/render";
import { TaskDetail } from "./TaskDetail";

function field(slug: string, name: string, type: string, options: Record<string, unknown> = {}, position = 0) {
  return { id: position + 1, slug, name, type, options, required: false, uniqueValue: false, position };
}

const collection = {
  id: 4,
  slug: "tasks",
  name: "To-dos",
  system: true,
  matchKeys: [],
  displayConfig: {},
  schemaVersion: 1,
  createdBy: "USER",
  fields: [
    field("state", "State", "STATUS", { states: ["open", "in_progress", "done"] }, 0),
    field("due_date", "Due", "DATE", {}, 1),
    field("priority", "Priority", "ENUM", { values: ["low", "normal", "high"] }, 2),
    field("notes", "Notes", "LONG_TEXT", {}, 3),
  ],
} as unknown as CollectionDto;

const task = {
  id: 1,
  collectionId: 4,
  title: "Send CV to Acme",
  values: { state: "open", due_date: "2026-10-08", priority: "high", notes: "Recruiting asked for the CV." },
  extra: {},
  createdAt: "2026-10-01T00:00:00Z",
  createdBy: "AI",
} as unknown as RecordDto;

function setup(overrides: Partial<Parameters<typeof TaskDetail>[0]> = {}, handlers: Parameters<typeof mockApi>[0] = {}) {
  const props = { collection, task, today: new Date(2026, 9, 8, 12, 0), onMove: vi.fn(), onChanged: vi.fn(), onClose: vi.fn(), ...overrides };
  const calls = mockApi({
    "PUT /api/collections/4/records/1": { body: task },
    "DELETE /api/collections/4/records/1": { status: 204 },
    "GET /api/collections/4/records/1/emails": { body: { items: [], page: 0, size: 5, totalElements: 0, totalPages: 0 } },
    ...handlers,
  });
  renderWithProviders(<TaskDetail {...props} />);
  return { props, calls };
}

function put(calls: ReturnType<typeof mockApi>) {
  return calls.find((call) => call.method === "PUT")?.body;
}

describe("TaskDetail", () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date(2026, 9, 8, 12, 0));
  });
  afterEach(() => vi.useRealTimers());

  it("shows all values of the to-do in editable fields and where it came from", async () => {
    setup();

    const sheet = await screen.findByRole("dialog", { name: "Send CV to Acme" });
    expect(within(sheet).getByLabelText("Due")).toHaveValue("2026-10-08");
    expect(within(sheet).getByRole("combobox", { name: "Priority" })).toHaveValue("high");
    expect(within(sheet).getByRole("textbox", { name: "Notes" })).toHaveValue("Recruiting asked for the CV.");
    expect(within(sheet).getByText("Due today")).toBeInTheDocument();
    expect(within(sheet).getByText(/Added by the assistant on 1 Oct 2026/)).toBeInTheDocument();
    expect(within(sheet).getByRole("link", { name: "Open as page" })).toHaveAttribute("href", "/collections/4/records/1");
  });

  it("saves only the changed title and values", async () => {
    const { props, calls } = setup();
    const sheet = await screen.findByRole("dialog", { name: "Send CV to Acme" });
    expect(within(sheet).getByRole("button", { name: "Save" })).toBeDisabled();

    const title = within(sheet).getByRole("textbox", { name: "Title" });
    await userEvent.clear(title);
    await userEvent.type(title, "Send the CV");
    await userEvent.selectOptions(within(sheet).getByRole("combobox", { name: "Priority" }), "low");
    await userEvent.click(within(sheet).getByRole("button", { name: "Save" }));

    await waitFor(() => expect(put(calls)).toEqual({ title: "Send the CV", values: { priority: "low" } }));
    await waitFor(() => expect(props.onChanged).toHaveBeenCalled());
  });

  it("clears the deadline to null", async () => {
    const { calls } = setup();
    const sheet = await screen.findByRole("dialog", { name: "Send CV to Acme" });

    await userEvent.clear(within(sheet).getByLabelText("Due"));
    await userEvent.click(within(sheet).getByRole("button", { name: "Save" }));

    await waitFor(() => expect(put(calls)).toEqual({ values: { due_date: null } }));
  });

  it("shows the server's validation message in the sheet", async () => {
    setup({}, { "PUT /api/collections/4/records/1": apiError(400, "Due is not a valid date") });
    const sheet = await screen.findByRole("dialog", { name: "Send CV to Acme" });

    await userEvent.type(within(sheet).getByRole("textbox", { name: "Notes" }), "!");
    await userEvent.click(within(sheet).getByRole("button", { name: "Save" }));

    expect(await within(sheet).findByRole("alert")).toHaveTextContent("Due is not a valid date");
  });

  it("asks before closing with unsaved changes", async () => {
    const { props } = setup();
    const sheet = await screen.findByRole("dialog", { name: "Send CV to Acme" });

    await userEvent.type(within(sheet).getByRole("textbox", { name: "Notes" }), "!");
    await userEvent.keyboard("{Escape}");

    expect(props.onClose).not.toHaveBeenCalled();
    expect(await within(sheet).findByText("Discard your changes?")).toBeInTheDocument();
    await userEvent.click(within(sheet).getByRole("button", { name: "Discard" }));
    expect(props.onClose).toHaveBeenCalled();
  });

  it("closes right away without changes", async () => {
    const { props } = setup();
    await screen.findByRole("dialog", { name: "Send CV to Acme" });

    await userEvent.keyboard("{Escape}");

    expect(props.onClose).toHaveBeenCalled();
  });

  it("moves the to-do with the quick actions", async () => {
    const { props } = setup();
    const sheet = await screen.findByRole("dialog", { name: "Send CV to Acme" });

    await userEvent.click(within(sheet).getByRole("button", { name: "Mark done" }));

    expect(props.onMove).toHaveBeenCalledWith("done");
  });

  it("deletes after a confirmation", async () => {
    const { props, calls } = setup();
    const sheet = await screen.findByRole("dialog", { name: "Send CV to Acme" });

    await userEvent.click(within(sheet).getByRole("button", { name: "Delete" }));
    expect(calls.some((call) => call.method === "DELETE")).toBe(false);
    await userEvent.click(within(sheet).getByRole("button", { name: "Delete to-do" }));

    await waitFor(() => expect(calls.some((call) => call.method === "DELETE" && call.path === "/api/collections/4/records/1")).toBe(true));
    await waitFor(() => expect(props.onClose).toHaveBeenCalled());
    expect(props.onChanged).toHaveBeenCalled();
  });
});
