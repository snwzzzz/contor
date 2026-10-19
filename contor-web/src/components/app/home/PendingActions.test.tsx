import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { mockApi } from "@/test/api-mock";
import { renderWithProviders } from "@/test/render";
import { PendingActions } from "./PendingActions";
import { COLLECTIONS, page } from "./test-fixtures";

const proposal = {
  id: 7,
  kind: "CREATE_RECORD",
  status: "PENDING",
  payload: { collectionId: 3, title: "Pay the invoice" },
  importance: "HIGH",
  importanceScore: 70,
  reasoning: "The supplier asked for payment by Friday.",
  createdAt: "2026-10-15T08:00:00Z",
};

describe("PendingActions", () => {
  it("shows the most important pending proposals with kind, priority and reasoning", async () => {
    mockApi({ "GET /api/proposals": { body: page([proposal]) }, "GET /api/collections": { body: COLLECTIONS } });
    renderWithProviders(<PendingActions />);

    const card = (await screen.findByText("Pay the invoice")).closest("li") as HTMLElement;
    expect(await within(card).findByText("New to-do")).toBeInTheDocument();
    expect(within(card).getByText("High")).toBeInTheDocument();
    expect(within(card).getByText("The supplier asked for payment by Friday.")).toBeInTheDocument();
    expect(within(card).getByRole("link", { name: "Review" })).toHaveAttribute("href", "/proposals");
    expect(screen.getByRole("link", { name: "View all" })).toHaveAttribute("href", "/proposals");
  });

  it("approves a proposal in place", async () => {
    const calls = mockApi({
      "GET /api/proposals": { body: page([proposal]) },
      "GET /api/collections": { body: COLLECTIONS },
      "POST /api/proposals/7/decision": { body: { ...proposal, status: "APPROVED" } },
    });
    renderWithProviders(<PendingActions />);

    await userEvent.click(await screen.findByRole("button", { name: "Approve" }));

    await waitFor(() =>
      expect(calls.find((call) => call.method === "POST")).toMatchObject({ path: "/api/proposals/7/decision", body: { decision: "APPROVE" } }),
    );
  });

  it("leaves draft replies and insights to the review page", async () => {
    mockApi({
      "GET /api/proposals": { body: page([{ ...proposal, kind: "DRAFT_REPLY", payload: { emailId: 8, subject: "Re: Offer" } }]) },
      "GET /api/collections": { body: [] },
    });
    renderWithProviders(<PendingActions />);

    expect(await screen.findByText("Re: Offer")).toBeInTheDocument();
    expect(screen.getByText("Draft reply")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Approve" })).not.toBeInTheDocument();
  });

  it("pages through the waiting proposals", async () => {
    mockApi({
      "GET /api/proposals": { body: page([1, 2, 3, 4].map((id) => ({ ...proposal, id, payload: { collectionId: 3, title: `Task ${id}` } }))) },
      "GET /api/collections": { body: COLLECTIONS },
    });
    renderWithProviders(<PendingActions />);

    expect(await screen.findByText("Task 3")).toBeInTheDocument();
    expect(screen.queryByText("Task 4")).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Next page" }));

    expect(screen.getByText("Task 4")).toBeInTheDocument();
  });

  it("says when nothing waits for a decision", async () => {
    mockApi({ "GET /api/proposals": { body: page([]) }, "GET /api/collections": { body: [] } });
    renderWithProviders(<PendingActions />);

    expect(await screen.findByText("Nothing waiting for you")).toBeInTheDocument();
  });
});
