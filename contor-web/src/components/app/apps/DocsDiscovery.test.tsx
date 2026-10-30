import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { ApiDiscoveryDto } from "@/lib/api/generated/model/apiDiscoveryDto";
import { mockApi } from "@/test/api-mock";
import { resetNavigation } from "@/test/next-navigation";
import { renderWithProviders } from "@/test/render";
import { DocsDiscovery } from "./DocsDiscovery";

vi.mock("next/navigation", () => import("@/test/next-navigation"));

const DOCS = "https://docs.shop.example";
const BASE = "https://api.shop.example/v1";

const done: ApiDiscoveryDto = {
  id: "d-1",
  state: "DONE",
  source: "DOCS",
  startedAt: "2026-10-29T12:00:00Z",
  endpoints: [
    { name: "Orders", method: "GET", path: "/orders", query: { status: "open" }, itemsPath: "$.data[*]", itemKeyPath: "$.id", source: "DOCS" },
    { name: "Products", method: "GET", path: "/products", query: {}, description: "The catalogue", source: "DOCS" },
  ],
};

describe("DocsDiscovery", () => {
  beforeEach(() => {
    resetNavigation("/calendar");
  });

  it("needs a documentation link", () => {
    mockApi({});
    renderWithProviders(<DocsDiscovery docsUrl="" baseUrl={BASE} onAdd={vi.fn()} />);

    expect(screen.getByRole("button", { name: "Read the docs" })).toBeDisabled();
    expect(screen.getByText("Add a documentation link under Basics to read it.")).toBeInTheDocument();
  });

  it("lists the suggestions of an OpenAPI document and adds the chosen ones", async () => {
    const recorded = mockApi({ "POST /api/external-apis/discovery": { status: 202, body: done } });
    const onAdd = vi.fn();
    renderWithProviders(<DocsDiscovery docsUrl={DOCS} baseUrl={BASE} apiId={7} onAdd={onAdd} />);

    await userEvent.click(screen.getByRole("button", { name: "Read the docs" }));

    expect(await screen.findByText("Read from the OpenAPI document.")).toBeInTheDocument();
    expect(recorded.find((call) => call.path === "/api/external-apis/discovery")?.body).toEqual({ docsUrl: DOCS, baseUrl: BASE, apiId: 7 });
    const suggestions = screen.getByRole("list", { name: "Suggested endpoints" });
    expect(within(suggestions).getAllByRole("checkbox")).toHaveLength(2);
    expect(within(suggestions).getByRole("checkbox", { name: /Orders/ })).toBeChecked();
    expect(within(suggestions).getByText("The catalogue")).toBeInTheDocument();

    await userEvent.click(within(suggestions).getByRole("checkbox", { name: /Products/ }));
    await userEvent.click(screen.getByRole("button", { name: "Add 1 endpoint" }));

    expect(onAdd).toHaveBeenCalledWith([
      expect.objectContaining({ name: "Orders", path: "/orders", itemsPath: "$.data[*]", itemKeyPath: "$.id", query: [expect.objectContaining({ name: "status", value: "open" })] }),
    ]);
  });

  it("waits while the assistant reads the docs and says that its suggestions need a look", async () => {
    mockApi({
      "POST /api/external-apis/discovery": { status: 202, body: { id: "d-2", state: "RUNNING", endpoints: [], startedAt: "2026-10-29T12:00:00Z" } },
      "GET /api/external-apis/discovery/d-2": { body: { ...done, id: "d-2", source: "MODEL" } },
    });
    renderWithProviders(<DocsDiscovery docsUrl={DOCS} baseUrl={BASE} onAdd={vi.fn()} />);

    await userEvent.click(screen.getByRole("button", { name: "Read the docs" }));

    expect(await screen.findByText("Read by the assistant. Check the paths before adding them.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Add 2 endpoints" })).toBeInTheDocument();
  });

  it("shows the reason when nothing could be read", async () => {
    mockApi({
      "POST /api/external-apis/discovery": {
        status: 202,
        body: { id: "d-3", state: "FAILED", endpoints: [], error: "The documentation answered with HTTP 404", startedAt: "2026-10-29T12:00:00Z" },
      },
    });
    renderWithProviders(<DocsDiscovery docsUrl={DOCS} baseUrl={BASE} onAdd={vi.fn()} />);

    await userEvent.click(screen.getByRole("button", { name: "Read the docs" }));

    expect(await screen.findByText("The documentation answered with HTTP 404")).toBeInTheDocument();
  });

  it("says so when the docs suggest nothing new", async () => {
    mockApi({ "POST /api/external-apis/discovery": { status: 202, body: { ...done, endpoints: [] } } });
    renderWithProviders(<DocsDiscovery docsUrl={DOCS} baseUrl={BASE} onAdd={vi.fn()} />);

    await userEvent.click(screen.getByRole("button", { name: "Read the docs" }));

    await waitFor(() => expect(screen.getByText("The docs suggest no endpoints this app does not have yet.")).toBeInTheDocument());
  });
});
