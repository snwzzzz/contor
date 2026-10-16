import { screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { mockApi } from "@/test/api-mock";
import { renderWithProviders } from "@/test/render";
import { HomeProposals } from "./HomeProposals";

const proposal = {
  id: 7,
  kind: "CREATE_RECORD",
  status: "PENDING",
  payload: { collectionId: 3, title: "Pay the invoice" },
  importance: "HIGH",
  importanceScore: 70,
  createdAt: "2026-10-15T08:00:00Z",
};

describe("HomeProposals", () => {
  it("lists the most important pending proposals and links to all of them", async () => {
    const calls = mockApi({
      "GET /api/proposals": { body: { items: [proposal], totalElements: 1, totalPages: 1, page: 0, size: 3 } },
      "GET /api/collections": { body: [{ id: 3, slug: "tasks", name: "To-dos", system: true, matchKeys: [], displayConfig: {}, schemaVersion: 1, createdBy: "USER", fields: [] }] },
    });
    renderWithProviders(<HomeProposals />);

    expect(await screen.findByText("Pay the invoice")).toBeInTheDocument();
    expect(await screen.findByText("New to-do")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "View all" })).toHaveAttribute("href", "/proposals");
    expect(calls.some((call) => call.path === "/api/proposals")).toBe(true);
  });

  it("titles a draft reply with its subject", async () => {
    mockApi({
      "GET /api/proposals": {
        body: { items: [{ ...proposal, kind: "DRAFT_REPLY", payload: { emailId: 8, subject: "Re: Offer" } }], totalElements: 1, totalPages: 1, page: 0, size: 3 },
      },
      "GET /api/collections": { body: [] },
    });
    renderWithProviders(<HomeProposals />);

    expect(await screen.findByText("Re: Offer")).toBeInTheDocument();
    expect(screen.getByText("Draft reply")).toBeInTheDocument();
  });

  it("describes each proposal with the assistant's reasoning", async () => {
    mockApi({
      "GET /api/proposals": {
        body: { items: [{ ...proposal, reasoning: "The supplier asked for payment by Friday." }], totalElements: 1, totalPages: 1, page: 0, size: 3 },
      },
      "GET /api/collections": { body: [] },
    });
    renderWithProviders(<HomeProposals />);

    expect(await screen.findByText("The supplier asked for payment by Friday.")).toBeInTheDocument();
  });

  it("says when nothing waits for a decision", async () => {
    mockApi({
      "GET /api/proposals": { body: { items: [], totalElements: 0, totalPages: 0, page: 0, size: 3 } },
      "GET /api/collections": { body: [] },
    });
    renderWithProviders(<HomeProposals />);

    expect(await screen.findByText("Nothing waiting for you")).toBeInTheDocument();
  });
});
