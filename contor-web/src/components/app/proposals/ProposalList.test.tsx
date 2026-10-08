import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { mockApi } from "@/test/api-mock";
import { renderWithProviders } from "@/test/render";
import { ProposalList } from "./ProposalList";

const collections = [
  { id: 1, slug: "people", name: "People", system: true, matchKeys: [], displayConfig: {}, schemaVersion: 1, createdBy: "USER", fields: [] },
  { id: 3, slug: "events", name: "Calendar", system: true, matchKeys: [], displayConfig: {}, schemaVersion: 1, createdBy: "USER", fields: [] },
];

const pending = [
  {
    id: 11,
    kind: "CREATE_RECORD",
    status: "PENDING",
    payload: { collectionId: 1, title: "Anna Berger", values: { email: "anna@acme.com" } },
    confidence: 0.95,
    reasoning: "Sender introduces herself",
    sourceEmailId: 8,
    createdAt: "2026-10-08T12:00:00Z",
  },
  {
    id: 12,
    kind: "UPDATE_RECORD",
    status: "PENDING",
    payload: { collectionId: 3, recordId: 4, values: { location: "Room 2" } },
    reasoning: "Room changed",
    createdAt: "2026-10-08T12:00:00Z",
  },
];

function page(items: unknown[]) {
  return { body: { items, page: 0, size: 50, totalElements: items.length, totalPages: 1 } };
}

describe("ProposalList", () => {
  it("lists pending proposals with what they would change", async () => {
    mockApi({ "GET /api/proposals": page(pending), "GET /api/collections": { body: collections } });

    renderWithProviders(<ProposalList />);

    const anna = await screen.findByRole("article", { name: "Anna Berger" });
    expect(within(anna).getByText("New record in People")).toBeInTheDocument();
    expect(within(anna).getByText("anna@acme.com")).toBeInTheDocument();
    expect(within(anna).getByText("Sender introduces herself")).toBeInTheDocument();
    expect(within(anna).getByText("95% sure")).toBeInTheDocument();
    const update = screen.getByRole("article", { name: "Record #4" });
    expect(within(update).getByText("Update in Calendar")).toBeInTheDocument();
  });

  it("approves and rejects", async () => {
    const calls = mockApi({
      "GET /api/proposals": page(pending),
      "GET /api/collections": { body: collections },
      "POST /api/proposals/11/decision": { body: { ...pending[0], status: "APPROVED" } },
      "POST /api/proposals/12/decision": { body: { ...pending[1], status: "REJECTED" } },
    });

    renderWithProviders(<ProposalList />);

    await userEvent.click(await screen.findByRole("button", { name: "Approve Anna Berger" }));
    await userEvent.click(screen.getByRole("button", { name: "Reject Record #4" }));

    await waitFor(() => {
      expect(calls.find((call) => call.path === "/api/proposals/11/decision")?.body).toEqual({ decision: "APPROVE" });
      expect(calls.find((call) => call.path === "/api/proposals/12/decision")?.body).toEqual({ decision: "REJECT" });
    });
  });

  it("says when there is nothing to review", async () => {
    mockApi({ "GET /api/proposals": page([]), "GET /api/collections": { body: collections } });

    renderWithProviders(<ProposalList />);

    expect(await screen.findByText("Nothing to review")).toBeInTheDocument();
  });
});
