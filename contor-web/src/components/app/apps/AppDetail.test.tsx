import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { ExternalApiCallDto } from "@/lib/api/generated/model/externalApiCallDto";
import type { ExternalApiEndpointDto } from "@/lib/api/generated/model/externalApiEndpointDto";
import { apiError, mockApi } from "@/test/api-mock";
import { appDto } from "@/test/external-api-fixtures";
import { resetNavigation } from "@/test/next-navigation";
import { renderWithProviders } from "@/test/render";
import { AppDetail } from "./AppDetail";

vi.mock("next/navigation", () => import("@/test/next-navigation"));

const shop = appDto(7, "Shop", {
  lastCalledAt: "2026-10-29T11:00:00Z",
  nextCallAt: "2026-10-29T17:00:00Z",
  status: "FAILING",
  failureCount: 3,
  lastError: "The app answered with HTTP 500",
});

function endpoint(id: number, name: string, overrides: Partial<ExternalApiEndpointDto> = {}): ExternalApiEndpointDto {
  return { id, apiId: 7, name, method: "GET", path: `/${name.toLowerCase()}`, query: {}, source: "USER", enabled: true, createdAt: "2026-10-28T08:00:00Z", ...overrides };
}

const calls: ExternalApiCallDto[] = [
  { id: 2, endpointId: 30, endpointName: "Orders", startedAt: "2026-10-29T11:00:00Z", durationMs: 140, httpStatus: 500, outcome: "FAILED",
    error: "The app answered with HTTP 500", itemsNew: 0, itemsChanged: 0, itemsGone: 0 },
  { id: 1, endpointId: 30, endpointName: "Orders", startedAt: "2026-10-29T05:00:00Z", durationMs: 120, httpStatus: 200, outcome: "OK",
    itemsNew: 2, itemsChanged: 1, itemsGone: 1 },
];

function api(overrides: Record<string, unknown> = {}) {
  return mockApi({
    "GET /api/external-apis/7": { body: shop },
    "GET /api/external-apis/7/endpoints": {
      body: [
        endpoint(30, "Orders", { mapping: { id: 40, collectionId: 4, state: "ACTIVE", fieldCount: 3 } }),
        endpoint(31, "Customers", { mapping: { id: 41, collectionId: 5, state: "PROPOSED", fieldCount: 2 } }),
        endpoint(32, "Status"),
      ],
    },
    "GET /api/external-apis/7/calls": { body: calls },
    "GET /api/collections": {
      body: [
        { id: 4, slug: "orders", name: "Orders", system: false, matchKeys: [], displayConfig: {}, schemaVersion: 1, createdBy: "USER", fields: [] },
        { id: 5, slug: "people", name: "People", system: true, matchKeys: [], displayConfig: {}, schemaVersion: 1, createdBy: "USER", fields: [] },
      ],
    },
    ...overrides,
  });
}

describe("AppDetail", () => {
  beforeEach(() => {
    resetNavigation("/apps/7");
    vi.useFakeTimers({ toFake: ["Date"], now: new Date("2026-10-29T12:00:00Z") });
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("shows the app's health, its endpoints with where their items go, and the call history", async () => {
    api();
    renderWithProviders(<AppDetail apiId={7} />);

    expect(await screen.findByRole("heading", { level: 1, name: /Shop/ })).toBeInTheDocument();
    const status = screen.getByRole("region", { name: "Status" });
    expect(status).toHaveTextContent("Failing");
    expect(status).toHaveTextContent("1 hr. ago");
    expect(status).toHaveTextContent("in 5 hr.");
    expect(status).toHaveTextContent("The app answered with HTTP 500");

    const endpoints = within(await screen.findByRole("region", { name: "Endpoints" }));
    expect(await endpoints.findByRole("listitem", { name: "Orders" })).toHaveTextContent("GET/orders");
    expect(endpoints.getByRole("listitem", { name: "Orders" })).toHaveTextContent("Into Orders · 3 fields");
    expect(endpoints.getByRole("link", { name: "Orders" })).toHaveAttribute("href", "/collections/4");
    expect(endpoints.getByRole("listitem", { name: "Customers" })).toHaveTextContent("Waiting for your approval");
    expect(endpoints.getByRole("link", { name: "Review" })).toHaveAttribute("href", "/proposals");
    expect(endpoints.getByRole("listitem", { name: "Status" })).toHaveTextContent("Not connected to a collection yet");

    const history = within(screen.getByRole("region", { name: "Call history" }));
    const rows = await history.findAllByRole("row");
    expect(rows[1]).toHaveTextContent("Failed");
    expect(rows[1]).toHaveTextContent("500");
    expect(rows[2]).toHaveTextContent("New answer");
    expect(rows[2]).toHaveTextContent("+2 ~1 −1");
  });

  it("calls the app now", async () => {
    const recorded = api({ "POST /api/external-apis/7/calls": { status: 202, body: { ...shop, nextCallAt: "2026-10-29T12:00:00Z" } } });
    renderWithProviders(<AppDetail apiId={7} />);

    await userEvent.click(await screen.findByRole("button", { name: "Call now" }));

    await waitFor(() => expect(recorded.some((call) => call.method === "POST" && call.path === "/api/external-apis/7/calls")).toBe(true));
  });

  it("pauses an app that is on and resumes one that is off", async () => {
    const recorded = api({ "PATCH /api/external-apis/7": { body: { ...shop, enabled: false } } });
    renderWithProviders(<AppDetail apiId={7} />);

    await userEvent.click(await screen.findByRole("button", { name: "Pause" }));

    await waitFor(() => expect(recorded.find((call) => call.method === "PATCH")?.body).toEqual({ enabled: false }));
    expect(await screen.findByRole("button", { name: "Resume" })).toBeInTheDocument();
  });

  it("edits the app in the modal over this page", async () => {
    api();
    renderWithProviders(<AppDetail apiId={7} />);

    expect(await screen.findByRole("link", { name: "Edit" })).toHaveAttribute("href", "/apps/7?app=7");
  });

  it("says so when the app does not exist", async () => {
    api({ "GET /api/external-apis/7": apiError(404, "App not found with id: 7") });
    renderWithProviders(<AppDetail apiId={7} />);

    expect(await screen.findByText("This app does not exist (any more).")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "All apps" })).toHaveAttribute("href", "/apps");
  });
});
