import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { mockApi, type RecordedCall } from "@/test/api-mock";
import { resetNavigation, router } from "@/test/next-navigation";
import { renderWithProviders } from "@/test/render";
import { CollectionTable } from "./CollectionTable";

vi.mock("next/navigation", () => import("@/test/next-navigation"));

function field(id: number, slug: string, name: string, type: string, options: Record<string, unknown> = {}) {
  return { id, slug, name, type, options, required: false, uniqueValue: false, position: id };
}

const deliveries = {
  id: 1,
  slug: "deliveries",
  name: "Deliveries",
  description: "Parcels on their way",
  system: false,
  matchKeys: [],
  displayConfig: {},
  schemaVersion: 1,
  createdBy: "AI",
  fields: [
    field(1, "carrier", "Carrier", "ENUM", { values: ["DHL", "UPS"] }),
    field(2, "eta", "ETA", "DATE"),
    field(3, "price", "Price", "MONEY"),
    field(4, "insured", "Insured", "BOOLEAN"),
  ],
};

function record(id: number, title: string, values: Record<string, unknown>) {
  return { id, collectionId: 1, title, values, extra: {}, createdAt: "2026-10-01T00:00:00Z", createdBy: "AI" };
}

const records = [
  record(5, "Shoes", { carrier: "DHL", eta: "2026-10-09", price: { amount: 59.9, currency: "EUR" }, insured: true }),
  record(6, "Books", { carrier: "UPS", eta: null, price: null, insured: false }),
];

function setup(): RecordedCall[] {
  return mockApi({
    "GET /api/collections/1": { body: deliveries },
    "GET /api/collections/1/records": { body: { items: records, page: 0, size: 50, totalElements: 2, totalPages: 1 } },
  });
}

function lastListUrl(calls: RecordedCall[], urls: URL[]): URL | undefined {
  return calls.length > 0 ? urls.at(-1) : undefined;
}

describe("CollectionTable", () => {
  beforeEach(() => resetNavigation("/collections/1"));
  afterEach(() => window.localStorage.clear());

  it("shows the records as a table with formatted values", async () => {
    setup();

    renderWithProviders(<CollectionTable collectionId={1} />);

    const table = await screen.findByRole("table", { name: "Deliveries" });
    expect(within(table).getAllByRole("columnheader").map((header) => header.textContent)).toEqual(["Title", "Carrier", "ETA", "Price", "Insured", ""]);
    const shoes = await within(table).findByRole("row", { name: /Shoes/ });
    expect(within(shoes).getByText("DHL")).toBeInTheDocument();
    expect(within(shoes).getByText("€59.90")).toBeInTheDocument();
    expect(within(shoes).getByText("Yes")).toBeInTheDocument();
    expect(screen.getByText("2 records")).toBeInTheDocument();
  });

  it("shows placeholder rows in the table while the records load, then the records", async () => {
    let release: (value: unknown) => void = () => undefined;
    mockApi({
      "GET /api/collections/1": { body: deliveries },
      "GET /api/collections/1/records": () =>
        new Promise((resolve) => {
          release = resolve;
        }).then(() => ({ body: { items: records, page: 0, size: 50, totalElements: 2, totalPages: 1 } })),
    });
    renderWithProviders(<CollectionTable collectionId={1} />);

    await screen.findByRole("table", { name: "Deliveries" });
    expect((await screen.findAllByTestId("skeleton-row")).length).toBeGreaterThan(0);

    release(undefined);
    expect(await screen.findByRole("row", { name: /Shoes/ })).toBeInTheDocument();
    expect(screen.queryByTestId("skeleton-row")).not.toBeInTheDocument();
  });

  it("searches across the collection", async () => {
    const urls: URL[] = [];
    const calls = mockApi({
      "GET /api/collections/1": { body: deliveries },
      "GET /api/collections/1/records": ({ url }) => {
        urls.push(url);
        return { body: { items: records, page: 0, size: 50, totalElements: 2, totalPages: 1 } };
      },
    });
    renderWithProviders(<CollectionTable collectionId={1} />);
    await screen.findByRole("table");

    await userEvent.type(screen.getByRole("searchbox", { name: "Search Deliveries" }), "zalando");

    await waitFor(() => expect(lastListUrl(calls, urls)?.searchParams.get("q")).toBe("zalando"));
  });

  it("filters by column", async () => {
    const urls: URL[] = [];
    mockApi({
      "GET /api/collections/1": { body: deliveries },
      "GET /api/collections/1/records": ({ url }) => {
        urls.push(url);
        return { body: { items: records, page: 0, size: 50, totalElements: 2, totalPages: 1 } };
      },
    });
    renderWithProviders(<CollectionTable collectionId={1} />);
    await screen.findByRole("table");

    await userEvent.click(screen.getByRole("button", { name: /Filters/ }));
    await userEvent.selectOptions(screen.getByRole("combobox", { name: "Filter Carrier" }), "DHL");
    await userEvent.type(screen.getByLabelText("Price from"), "20");

    await waitFor(() => expect(urls.at(-1)?.searchParams.getAll("filter")).toEqual(["carrier:eq:DHL", "price:gte:20"]));
    expect(screen.getByRole("button", { name: /Filters/ })).toHaveTextContent("2");
  });

  it("sorts by a column", async () => {
    const urls: URL[] = [];
    mockApi({
      "GET /api/collections/1": { body: deliveries },
      "GET /api/collections/1/records": ({ url }) => {
        urls.push(url);
        return { body: { items: records, page: 0, size: 50, totalElements: 2, totalPages: 1 } };
      },
    });
    renderWithProviders(<CollectionTable collectionId={1} />);
    await screen.findByRole("table");

    await userEvent.click(screen.getByRole("button", { name: "Sort by ETA" }));
    await waitFor(() => expect(urls.at(-1)?.searchParams.get("sort")).toBe("eta"));
    await userEvent.click(screen.getByRole("button", { name: "Sort by ETA" }));
    await waitFor(() => expect(urls.at(-1)?.searchParams.get("sort")).toBe("-eta"));
    expect(screen.getByRole("columnheader", { name: /ETA/ })).toHaveAttribute("aria-sort", "descending");
  });

  it("opens a record on double click or Enter", async () => {
    setup();
    renderWithProviders(<CollectionTable collectionId={1} />);

    fireEvent.doubleClick(await screen.findByRole("row", { name: /Shoes/ }));
    expect(router.push).toHaveBeenCalledWith("/collections/1/records/5");

    screen.getByRole("row", { name: /Books/ }).focus();
    await userEvent.keyboard("{Enter}");
    expect(router.push).toHaveBeenCalledWith("/collections/1/records/6");
  });

  function recording(totalPages = 1) {
    const urls: URL[] = [];
    mockApi({
      "GET /api/collections/1": { body: deliveries },
      "GET /api/collections/1/records": ({ url }) => {
        urls.push(url);
        return { body: { items: records, page: Number(url.searchParams.get("page") ?? 0), size: 25, totalElements: totalPages * 25, totalPages } };
      },
    });
    return urls;
  }

  it("is a plain table (no colors) with column separators and opens a record from its row action", async () => {
    setup();
    renderWithProviders(<CollectionTable collectionId={1} />);

    const shoes = await screen.findByRole("row", { name: /Shoes/ });
    // Choice values are plain text; nothing in the table carries a tint.
    expect(within(shoes).getByText("DHL").className).not.toMatch(/tint/);
    expect(screen.getByRole("table").querySelector("[class*='tint-']")).toBeNull();
    // Roomy rows with hairlines between the columns, sentence-case headers.
    const [first, second] = within(shoes).getAllByRole("cell");
    expect(first).toHaveClass("py-3.5", "first:border-l-0");
    expect(second).toHaveClass("border-l");
    // Separate borders: with collapsed ones the sticky header leaves its lines behind while scrolling.
    expect(screen.getByRole("table")).toHaveClass("border-separate", "border-spacing-0");
    expect(first).toHaveClass("border-b");
    expect(screen.getByRole("button", { name: "Sort by Title" })).not.toHaveClass("uppercase");
    // The reports do not take room on the page: they open from a toolbar button.
    expect(screen.queryByRole("textbox", { name: /Ask about/ })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: /^Reports/ })).toBeInTheDocument();
    // The table takes the rest of the page down to the record count and scrolls on its own.
    const scroller = screen.getByRole("table").parentElement;
    expect(scroller).toHaveClass("flex-1", "min-h-0", "overflow-auto");
    expect(scroller?.parentElement).toHaveClass("flex-1", "flex-col");

    await userEvent.click(within(shoes).getByRole("button", { name: "Open Shoes" }));
    expect(router.push).toHaveBeenCalledWith("/collections/1/records/5");
  });

  it("changes the number of rows per page", async () => {
    const urls = recording();
    renderWithProviders(<CollectionTable collectionId={1} />);
    await screen.findByRole("table");

    await userEvent.selectOptions(screen.getByRole("combobox", { name: "Rows per page" }), "100");

    await waitFor(() => expect(urls.at(-1)?.searchParams.get("size")).toBe("100"));
  });

  it("hides columns and remembers it per collection", async () => {
    recording();
    renderWithProviders(<CollectionTable collectionId={1} />);
    await screen.findByRole("table");

    await userEvent.click(screen.getByRole("button", { name: "Columns" }));
    await userEvent.click(await screen.findByRole("menuitemcheckbox", { name: "ETA" }));
    await userEvent.keyboard("{Escape}");

    await waitFor(() =>
      expect(screen.getAllByRole("columnheader").map((header) => header.textContent)).toEqual(["Title", "Carrier", "Price", "Insured", ""]),
    );
    expect(JSON.parse(window.localStorage.getItem("contor.table.1.hiddenColumns") ?? "[]")).toEqual(["eta"]);
  });

  it("pages with numbers and jumps to a page", async () => {
    const urls = recording(12);
    renderWithProviders(<CollectionTable collectionId={1} />);
    await screen.findByRole("table");

    const pages = await screen.findByRole("navigation", { name: "Pages" });
    expect(within(pages).getByRole("button", { name: "Page 1" })).toHaveAttribute("aria-current", "page");
    await userEvent.click(within(pages).getByRole("button", { name: "Page 12" }));
    await waitFor(() => expect(urls.at(-1)?.searchParams.get("page")).toBe("11"));

    await userEvent.type(screen.getByRole("spinbutton", { name: "Go to page" }), "5{Enter}");
    await waitFor(() => expect(urls.at(-1)?.searchParams.get("page")).toBe("4"));
  });

  it("offers to clear the search when nothing matches it", async () => {
    const urls: URL[] = [];
    mockApi({
      "GET /api/collections/1": { body: deliveries },
      "GET /api/collections/1/records": ({ url }) => {
        urls.push(url);
        return { body: { items: [], page: 0, size: 25, totalElements: 0, totalPages: 0 } };
      },
    });
    renderWithProviders(<CollectionTable collectionId={1} />);
    await screen.findByRole("table");

    await userEvent.type(screen.getByRole("searchbox", { name: "Search Deliveries" }), "nothing");
    await userEvent.click(await screen.findByRole("button", { name: "Clear search and filters" }));

    // Back to the unfiltered list (served from the query cache, so no new request is needed).
    expect(await screen.findByText("No records")).toBeInTheDocument();
    expect(screen.queryByText("No matching records")).not.toBeInTheDocument();
    expect(screen.getByRole("searchbox", { name: "Search Deliveries" })).toHaveValue("");
    expect(urls.some((url) => url.searchParams.get("q") === "nothing")).toBe(true);
  });

  it("says when nothing matches", async () => {
    mockApi({
      "GET /api/collections/1": { body: deliveries },
      "GET /api/collections/1/records": { body: { items: [], page: 0, size: 50, totalElements: 0, totalPages: 0 } },
    });

    renderWithProviders(<CollectionTable collectionId={1} />);

    expect(await screen.findByText("No records")).toBeInTheDocument();
  });
});
