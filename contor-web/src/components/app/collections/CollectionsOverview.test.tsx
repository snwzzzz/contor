import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { mockApi } from "@/test/api-mock";
import { renderWithProviders } from "@/test/render";
import { CollectionsOverview } from "./CollectionsOverview";

function collection(id: number, name: string, extra: Record<string, unknown> = {}) {
  return {
    id,
    slug: name.toLowerCase(),
    name,
    system: false,
    matchKeys: [],
    displayConfig: {},
    schemaVersion: 1,
    createdBy: "USER",
    fields: [
      { id: id * 10, slug: "a", name: "A", type: "TEXT", required: false, position: 0 },
      { id: id * 10 + 1, slug: "b", name: "B", type: "TEXT", required: false, position: 1, archivedAt: "2026-10-01T00:00:00Z" },
    ],
    ...extra,
  };
}

const COLLECTIONS = [
  collection(2, "Organizations", { system: true, description: "Companies you deal with." }),
  collection(1, "People", { system: true, description: "Contacts and senders." }),
  collection(5, "Deliveries", { description: "Incoming goods." }),
  collection(7, "Invoices", { createdBy: "AI" }),
];

function api(collections: unknown[] = COLLECTIONS) {
  return mockApi({
    "GET /api/collections": { body: collections },
    "GET /api/collections/1/records": { body: { items: [], page: 0, size: 1, totalElements: 42, totalPages: 42 } },
    "GET /api/collections/2/records": { body: { items: [], page: 0, size: 1, totalElements: 7, totalPages: 7 } },
    "GET /api/collections/5/records": { body: { items: [], page: 0, size: 1, totalElements: 0, totalPages: 0 } },
    "GET /api/collections/7/records": { body: { items: [], page: 0, size: 1, totalElements: 3, totalPages: 3 } },
  });
}

const names = () => screen.getAllByRole("row").slice(1).map((row) => within(row).getByRole("link").textContent);

describe("CollectionsOverview", () => {
  it("lists every collection by name with its description, records, fields and origin", async () => {
    api();
    renderWithProviders(<CollectionsOverview />);

    expect(await screen.findByRole("link", { name: "People" })).toHaveAttribute("href", "/collections/1");
    expect(names()).toEqual(["Deliveries", "Invoices", "Organizations", "People"]);

    const people = screen.getByRole("link", { name: "People" }).closest("tr") as HTMLElement;
    expect(within(people).getByText("Contacts and senders.")).toBeInTheDocument();
    expect(await within(people).findByText("42")).toBeInTheDocument();
    // Archived fields do not count.
    expect(within(people).getByText("1")).toBeInTheDocument();
    expect(within(people).getByText("Built-in")).toBeInTheDocument();
    const invoices = screen.getByRole("link", { name: "Invoices" }).closest("tr") as HTMLElement;
    expect(within(invoices).getByText("By the assistant")).toBeInTheDocument();
    expect(screen.getByText("4 collections")).toBeInTheDocument();
  });

  it("filters by origin and by search text", async () => {
    api();
    renderWithProviders(<CollectionsOverview />);
    await screen.findByRole("link", { name: "People" });

    await userEvent.click(screen.getByRole("button", { name: /Built-in/ }));
    expect(names()).toEqual(["Organizations", "People"]);

    await userEvent.click(screen.getByRole("button", { name: /Yours/ }));
    expect(names()).toEqual(["Deliveries"]);

    await userEvent.click(screen.getByRole("button", { name: /All/ }));
    await userEvent.type(screen.getByRole("searchbox", { name: "Search collections" }), "compan");
    expect(names()).toEqual(["Organizations"]);
    expect(screen.getByText("1 collection")).toBeInTheDocument();
  });

  it("says when nothing matches and shows all again", async () => {
    api();
    renderWithProviders(<CollectionsOverview />);
    await screen.findByRole("link", { name: "People" });

    await userEvent.type(screen.getByRole("searchbox", { name: "Search collections" }), "zzz");

    expect(screen.getByText("No collection matches")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Show all" }));
    expect(names()).toHaveLength(4);
    expect(screen.getByRole("searchbox", { name: "Search collections" })).toHaveValue("");
  });

  it("invites to create the first collection", async () => {
    api([]);
    renderWithProviders(<CollectionsOverview />);

    expect(await screen.findByText("No collections yet")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "New collection" })).toHaveAttribute("href", "/collections/new");
  });

  it("shows skeleton rows while loading", () => {
    mockApi({ "GET /api/collections": () => new Promise(() => {}) });
    renderWithProviders(<CollectionsOverview />);

    expect(screen.getByLabelText("Loading collections")).toBeInTheDocument();
  });
});
