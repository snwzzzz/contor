import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { ExternalApiDto } from "@/lib/api/generated/model/externalApiDto";
import type { ExternalApiEndpointDto } from "@/lib/api/generated/model/externalApiEndpointDto";
import { apiError, mockApi, type RecordedCall } from "@/test/api-mock";
import { navigationState, resetNavigation, router } from "@/test/next-navigation";
import { renderWithProviders } from "@/test/render";
import { AppDialog } from "./AppDialog";

vi.mock("next/navigation", () => import("@/test/next-navigation"));

const saved: ExternalApiDto = {
  id: 7,
  name: "Shop",
  slug: "app_shop",
  baseUrl: "https://api.shop.example/v1",
  authKind: "BEARER",
  authConfig: {},
  hasSecret: true,
  headers: [],
  secretHeaderNames: [],
  pollIntervalMinutes: 360,
  enabled: true,
  aiAnalysis: true,
  status: "ACTIVE",
  failureCount: 0,
  dailyRequestLimit: 200,
  endpointCount: 1,
  createdAt: "2026-10-29T08:00:00Z",
};

const ordersEndpoint: ExternalApiEndpointDto = {
  id: 3,
  apiId: 7,
  name: "Orders",
  method: "GET",
  path: "/orders",
  query: {},
  source: "USER",
  enabled: true,
  createdAt: "2026-10-29T08:00:00Z",
};

const answer = { ok: true, status: 200, contentType: "application/json", body: '{"data":[{"id":1},{"id":2}]}', bodyTruncated: false, durationMs: 120 };

function openAt(search: string) {
  navigationState.search = search;
  return renderWithProviders(<AppDialog />);
}

function calls(recorded: RecordedCall[], method: string, path: string) {
  return recorded.filter((call) => call.method === method && call.path === path);
}

async function fillBasics() {
  await userEvent.type(screen.getByLabelText("Name"), "Shop");
  await userEvent.type(screen.getByLabelText("Base URL"), "https://api.shop.example/v1");
}

describe("AppDialog", () => {
  beforeEach(() => {
    resetNavigation("/calendar");
  });

  it("stays closed without the app parameter", () => {
    mockApi({});
    openAt("");

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  describe("adding an app", () => {
    it("checks each step before going on", async () => {
      mockApi({});
      openAt("app=new");

      expect(screen.getByRole("dialog", { name: "Add app" })).toBeInTheDocument();
      await userEvent.click(screen.getByRole("button", { name: "Next step" }));

      expect(screen.getByLabelText("Name")).toHaveAccessibleDescription("Enter a name.");
      expect(screen.getByLabelText("Base URL")).toHaveAccessibleDescription("Enter the base URL.");

      await fillBasics();
      await userEvent.click(screen.getByRole("button", { name: "Next step" }));
      await userEvent.selectOptions(screen.getByLabelText("Authentication"), "BEARER");
      await userEvent.click(screen.getByRole("button", { name: "Next step" }));

      expect(screen.getByLabelText("Token")).toHaveAccessibleDescription("Enter the token.");
    });

    it("tests an endpoint before saving, takes the detected items and saves the app with its endpoints", async () => {
      const recorded = mockApi({
        "POST /api/external-apis/test": { body: answer },
        "POST /api/external-apis": { status: 201, body: saved },
        "POST /api/external-apis/7/endpoints": { status: 201, body: ordersEndpoint },
      });
      openAt("app=new");

      await fillBasics();
      await userEvent.click(screen.getByRole("button", { name: "Next step" }));
      await userEvent.selectOptions(screen.getByLabelText("Authentication"), "BEARER");
      await userEvent.type(screen.getByLabelText("Token"), "tok-1");
      await userEvent.click(screen.getByRole("button", { name: "Add header" }));
      await userEvent.type(screen.getByLabelText("Header 1 name"), "X-Tenant");
      await userEvent.type(screen.getByLabelText("Header 1 value"), "t1");
      await userEvent.click(screen.getByRole("checkbox", { name: "Header 1 is secret" }));
      expect(screen.getByLabelText("Header 1 value")).toHaveAttribute("type", "password");
      await userEvent.click(screen.getByRole("button", { name: "Next step" }));

      await userEvent.click(screen.getByRole("button", { name: "Add endpoint" }));
      await userEvent.type(screen.getByLabelText("Endpoint name"), "Orders");
      await userEvent.type(screen.getByLabelText("Path"), "/orders");
      await userEvent.click(screen.getByRole("button", { name: "Test call" }));

      expect(await screen.findByText(/200 · 120 ms/)).toBeInTheDocument();
      expect(calls(recorded, "POST", "/api/external-apis/test")[0].body).toEqual({
        baseUrl: "https://api.shop.example/v1",
        authKind: "BEARER",
        authConfig: { headerName: null, queryParam: null, tokenUrl: null, scope: null, username: null, clientId: null },
        secret: "tok-1",
        headers: [],
        secretHeaders: [{ name: "X-Tenant", value: "t1" }],
        method: "GET",
        path: "/orders",
        query: {},
      });
      expect(screen.getByText("2 items at $.data[*]")).toBeInTheDocument();
      await userEvent.click(screen.getByRole("button", { name: "Use" }));
      expect(screen.getByLabelText("Items path")).toHaveValue("$.data[*]");
      expect(screen.getByLabelText("Item key")).toHaveValue("$.id");
      await userEvent.click(screen.getByRole("button", { name: "Keep endpoint" }));

      expect(screen.getByRole("listitem", { name: /Orders/ })).toHaveTextContent("GET/orders");
      await userEvent.click(screen.getByRole("button", { name: "Next step" }));
      await userEvent.click(screen.getByRole("radio", { name: "Every hour" }));
      await userEvent.click(screen.getByRole("button", { name: "Add app" }));

      await waitFor(() => expect(router.replace).toHaveBeenCalledWith("/calendar", { scroll: false }));
      expect(calls(recorded, "POST", "/api/external-apis")[0].body).toMatchObject({
        name: "Shop",
        authKind: "BEARER",
        secret: "tok-1",
        secretHeaders: [{ name: "X-Tenant", value: "t1" }],
        pollIntervalMinutes: 60,
        enabled: true,
        aiAnalysis: true,
      });
      expect(calls(recorded, "POST", "/api/external-apis/7/endpoints")[0].body).toEqual({
        name: "Orders",
        method: "GET",
        path: "/orders",
        query: {},
        itemsPath: "$.data[*]",
        itemKeyPath: "$.id",
        enabled: true,
      });
    });

    it("adds the endpoints the docs suggest to the new app", async () => {
      const recorded = mockApi({
        "POST /api/external-apis/discovery": {
          status: 202,
          body: { id: "d-1", state: "DONE", source: "DOCS", startedAt: "2026-10-29T12:00:00Z",
            endpoints: [{ name: "Orders", method: "GET", path: "/orders", query: {}, itemsPath: "$.data[*]", source: "DOCS" }] },
        },
      });
      openAt("app=new");
      await fillBasics();
      await userEvent.type(screen.getByLabelText("Documentation link"), "https://docs.shop.example");
      await userEvent.click(screen.getByRole("button", { name: "Next step" }));
      await userEvent.click(screen.getByRole("button", { name: "Next step" }));

      await userEvent.click(screen.getByRole("button", { name: "Read the docs" }));
      await userEvent.click(await screen.findByRole("button", { name: "Add 1 endpoint" }));

      expect(screen.getByRole("listitem", { name: "Orders" })).toHaveTextContent("GET/orders");
      expect(calls(recorded, "POST", "/api/external-apis/discovery")[0].body).toEqual({
        docsUrl: "https://docs.shop.example",
        baseUrl: "https://api.shop.example/v1",
      });
    });

    it("shows why a test call failed", async () => {
      mockApi({
        "POST /api/external-apis/test": { body: { ok: false, bodyTruncated: false, durationMs: 3, error: "api.shop.example is not a public address" } },
      });
      openAt("app=new");
      await fillBasics();
      await userEvent.click(screen.getByRole("button", { name: "Next step" }));
      await userEvent.click(screen.getByRole("button", { name: "Next step" }));
      await userEvent.click(screen.getByRole("button", { name: "Add endpoint" }));
      await userEvent.type(screen.getByLabelText("Endpoint name"), "Orders");
      await userEvent.type(screen.getByLabelText("Path"), "/orders");

      await userEvent.click(screen.getByRole("button", { name: "Test call" }));

      expect(await screen.findByText("api.shop.example is not a public address")).toBeInTheDocument();
    });

    it("opens the saved app when an endpoint could not be added, so it can be fixed there", async () => {
      mockApi({
        "POST /api/external-apis": { status: 201, body: saved },
        "POST /api/external-apis/7/endpoints": apiError(400, "The path is not valid"),
      });
      openAt("app=new");
      await fillBasics();
      await userEvent.click(screen.getByRole("button", { name: "Next step" }));
      await userEvent.click(screen.getByRole("button", { name: "Next step" }));
      await userEvent.click(screen.getByRole("button", { name: "Add endpoint" }));
      await userEvent.type(screen.getByLabelText("Endpoint name"), "Orders");
      await userEvent.type(screen.getByLabelText("Path"), "/orders");
      await userEvent.click(screen.getByRole("button", { name: "Keep endpoint" }));
      await userEvent.click(screen.getByRole("button", { name: "Next step" }));

      await userEvent.click(screen.getByRole("button", { name: "Add app" }));

      await waitFor(() => expect(router.replace).toHaveBeenCalledWith("/calendar?app=7", { scroll: false }));
    });
  });

  describe("editing an app", () => {
    it("never shows the stored secret, and saves only what changed", async () => {
      const recorded = mockApi({
        "GET /api/external-apis/7": { body: saved },
        "GET /api/external-apis/7/endpoints": { body: [ordersEndpoint] },
        "PATCH /api/external-apis/7": { body: { ...saved, name: "Shop AT" } },
      });
      openAt("app=7");

      expect(await screen.findByRole("dialog", { name: "Shop" })).toBeInTheDocument();
      const save = screen.getByRole("button", { name: "Save" });
      expect(save).toBeDisabled();

      await userEvent.click(screen.getByRole("tab", { name: "Auth & headers" }));
      expect(screen.getByText("Token stored")).toBeInTheDocument();
      expect(screen.queryByLabelText("Token")).not.toBeInTheDocument();

      await userEvent.click(screen.getByRole("tab", { name: "Basics" }));
      await userEvent.clear(screen.getByLabelText("Name"));
      await userEvent.type(screen.getByLabelText("Name"), "Shop AT");
      await userEvent.click(save);

      await waitFor(() => expect(calls(recorded, "PATCH", "/api/external-apis/7")).toHaveLength(1));
      expect(calls(recorded, "PATCH", "/api/external-apis/7")[0].body).toEqual({ name: "Shop AT" });
    });

    it("replaces the stored secret only when a new one is typed", async () => {
      const recorded = mockApi({
        "GET /api/external-apis/7": { body: saved },
        "GET /api/external-apis/7/endpoints": { body: [] },
        "PATCH /api/external-apis/7": { body: saved },
      });
      openAt("app=7");
      await userEvent.click(await screen.findByRole("tab", { name: "Auth & headers" }));

      await userEvent.click(screen.getByRole("button", { name: "Replace" }));
      await userEvent.click(screen.getByRole("button", { name: "Save" }));
      expect(screen.getByLabelText("Token")).toHaveAccessibleDescription("Enter the token.");

      await userEvent.type(screen.getByLabelText("Token"), "tok-2");
      await userEvent.click(screen.getByRole("button", { name: "Save" }));

      await waitFor(() => expect(calls(recorded, "PATCH", "/api/external-apis/7")[0]?.body).toEqual({ secret: "tok-2" }));
    });

    it("lists, tests and removes endpoints right away", async () => {
      const recorded = mockApi({
        "GET /api/external-apis/7": { body: saved },
        "GET /api/external-apis/7/endpoints": { body: [ordersEndpoint] },
        "POST /api/external-apis/test": { body: answer },
        "DELETE /api/external-apis/7/endpoints/3": { status: 204 },
      });
      openAt("app=7");
      await userEvent.click(await screen.findByRole("tab", { name: "Endpoints" }));

      const row = await screen.findByRole("listitem", { name: /Orders/ });
      await userEvent.click(within(row).getByRole("button", { name: "Edit Orders" }));
      await userEvent.click(screen.getByRole("button", { name: "Test call" }));
      expect(await screen.findByText(/200 · 120 ms/)).toBeInTheDocument();
      expect(calls(recorded, "POST", "/api/external-apis/test")[0].body).toMatchObject({ apiId: 7, path: "/orders" });
      expect(calls(recorded, "POST", "/api/external-apis/test")[0].body).not.toHaveProperty("secret");
      await userEvent.click(screen.getByRole("button", { name: "Cancel" }));

      await userEvent.click(within(screen.getByRole("listitem", { name: /Orders/ })).getByRole("button", { name: "Remove Orders" }));

      await waitFor(() => expect(calls(recorded, "DELETE", "/api/external-apis/7/endpoints/3")).toHaveLength(1));
    });

    it("adds suggested endpoints to the saved app right away", async () => {
      const recorded = mockApi({
        "GET /api/external-apis/7": { body: { ...saved, docsUrl: "https://docs.shop.example" } },
        "GET /api/external-apis/7/endpoints": { body: [ordersEndpoint] },
        "POST /api/external-apis/discovery": {
          status: 202,
          body: { id: "d-1", state: "DONE", source: "MODEL", startedAt: "2026-10-29T12:00:00Z",
            endpoints: [{ name: "Products", method: "GET", path: "/products", query: { limit: "50" }, source: "MODEL" }] },
        },
        "POST /api/external-apis/7/endpoints": { status: 201, body: { ...ordersEndpoint, id: 4, name: "Products", path: "/products" } },
      });
      openAt("app=7");
      await userEvent.click(await screen.findByRole("tab", { name: "Endpoints" }));

      await userEvent.click(await screen.findByRole("button", { name: "Read the docs" }));
      await userEvent.click(await screen.findByRole("button", { name: "Add 1 endpoint" }));

      await waitFor(() => expect(calls(recorded, "POST", "/api/external-apis/7/endpoints")).toHaveLength(1));
      expect(calls(recorded, "POST", "/api/external-apis/discovery")[0].body).toMatchObject({ apiId: 7 });
      expect(calls(recorded, "POST", "/api/external-apis/7/endpoints")[0].body).toEqual({
        name: "Products",
        method: "GET",
        path: "/products",
        query: { limit: "50" },
        enabled: true,
      });
    });

    it("deletes the app after a confirmation", async () => {
      const recorded = mockApi({
        "GET /api/external-apis/7": { body: saved },
        "GET /api/external-apis/7/endpoints": { body: [] },
        "DELETE /api/external-apis/7": { status: 204 },
      });
      openAt("app=7");

      await userEvent.click(await screen.findByRole("button", { name: "Delete app" }));
      expect(calls(recorded, "DELETE", "/api/external-apis/7")).toHaveLength(0);
      await userEvent.click(screen.getByRole("button", { name: "Yes, delete Shop" }));

      await waitFor(() => expect(router.replace).toHaveBeenCalledWith("/calendar", { scroll: false }));
      expect(calls(recorded, "DELETE", "/api/external-apis/7")).toHaveLength(1);
    });

    it("says so when the app does not exist", async () => {
      mockApi({ "GET /api/external-apis/8": apiError(404, "App not found with id: 8") });
      openAt("app=8");

      expect(await screen.findByText("This app does not exist (any more).")).toBeInTheDocument();
    });
  });
});
