import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { ExternalApiEndpointDto } from "@/lib/api/generated/model/externalApiEndpointDto";
import { mockApi } from "@/test/api-mock";
import { appDto } from "@/test/external-api-fixtures";
import { resetNavigation } from "@/test/next-navigation";
import { renderWithProviders } from "@/test/render";
import { EndpointData } from "./EndpointData";

vi.mock("next/navigation", () => import("@/test/next-navigation"));

const popular: ExternalApiEndpointDto = {
  id: 30, apiId: 7, name: "Popular Movies", method: "GET", path: "/movie/popular", query: {}, itemsPath: "$[*]", source: "MODEL",
  enabled: true, createdAt: "2026-10-29T08:00:00Z",
};

const answer = { fetchedAt: "2026-10-29T11:00:00Z", contentType: "application/json", truncated: false,
  body: '{"page":1,"results":[{"id":1058424,"title":"Hope"},{"id":1416,"title":"Grey\'s Anatomy"}]}' };

function open(endpoint = popular) {
  return renderWithProviders(<EndpointData api={appDto(7, "themoviedb")} endpoint={endpoint} open onOpenChange={vi.fn()} />);
}

describe("EndpointData", () => {
  beforeEach(() => {
    resetNavigation("/apps/7");
    vi.useFakeTimers({ toFake: ["Date"], now: new Date("2026-10-29T12:00:00Z") });
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("lists the items read from the answers with their records, and shows the last answer", async () => {
    mockApi({
      "GET /api/external-apis/7/endpoints/30/data": {
        body: {
          answer,
          itemCount: 2,
          goneCount: 1,
          items: [
            { id: 9, key: "1058424", data: { id: 1058424, title: "Hope" }, firstSeenAt: "2026-10-29T11:00:00Z", lastSeenAt: "2026-10-29T11:00:00Z",
              collectionId: 4, recordId: 3 },
            { id: 10, key: "1416", data: { id: 1416, name: "Grey's Anatomy" }, firstSeenAt: "2026-10-29T11:00:00Z", lastSeenAt: "2026-10-29T11:00:00Z" },
          ],
        },
      },
    });
    open({ ...popular, itemsPath: "$.results[*]", itemKeyPath: "$.id" });

    expect(await screen.findByRole("dialog", { name: "Popular Movies" })).toBeInTheDocument();
    expect(await screen.findByText("2 items · 1 gone · last answer 1 hr. ago")).toBeInTheDocument();
    expect(screen.getByRole("listitem", { name: "Hope" })).toHaveTextContent("1058424");
    expect(screen.getByRole("link", { name: "Open record" })).toHaveAttribute("href", "/collections/4/records/3");
    expect(screen.getByRole("listitem", { name: "Grey's Anatomy" })).toBeInTheDocument();

    vi.useRealTimers();
    await userEvent.click(screen.getByRole("tab", { name: "Last answer" }));
    expect(await screen.findByText(/"title": "Hope"/)).toBeInTheDocument();
  });

  it("suggests where the items are when the path reads none, and fixes it in one click", async () => {
    const recorded = mockApi({
      "GET /api/external-apis/7/endpoints/30/data": { body: { answer, itemCount: 0, goneCount: 0, items: [] } },
      "PATCH /api/external-apis/7/endpoints/30": { body: { ...popular, itemsPath: "$.results[*]", itemKeyPath: "$.id" } },
      "POST /api/external-apis/7/calls": { status: 202, body: appDto(7, "themoviedb") },
    });
    open();

    expect(await screen.findByText("No items were read: the items path $[*] finds nothing in the answer.")).toBeInTheDocument();
    expect(screen.getByText("The answer has 2 items at $.results[*], identified by $.id.")).toBeInTheDocument();
    vi.useRealTimers();
    await userEvent.click(screen.getByRole("button", { name: "Use these paths" }));

    await waitFor(() => expect(recorded.some((call) => call.method === "POST" && call.path === "/api/external-apis/7/calls")).toBe(true));
    expect(recorded.find((call) => call.method === "PATCH")?.body).toEqual({ itemsPath: "$.results[*]", itemKeyPath: "$.id" });
  });

  it("says when nothing has come back yet", async () => {
    mockApi({ "GET /api/external-apis/7/endpoints/30/data": { body: { itemCount: 0, goneCount: 0, items: [] } } });
    open();

    expect(await screen.findByText("No answer yet. The first call comes with the next poll.")).toBeInTheDocument();
  });
});
