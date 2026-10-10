import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { mockApi, type MockResponse } from "@/test/api-mock";
import { renderWithProviders } from "@/test/render";
import { ProposalList } from "./ProposalList";

function collection(id: number, slug: string, name: string) {
  return { id, slug, name, system: true, matchKeys: [], displayConfig: {}, schemaVersion: 1, createdBy: "USER", fields: [] };
}

const collections = [collection(3, "events", "Calendar"), collection(4, "tasks", "To-dos")];

const actions = [
  {
    id: 11,
    kind: "CREATE_RECORD",
    status: "PENDING",
    payload: { collectionId: 3, title: "Interview at Acme", values: { starts_at: "2026-10-09T07:30:00Z", location: "Acme HQ" } },
    confidence: 0.95,
    reasoning: "Invitation to an interview on Friday",
    createdAt: "2026-10-08T12:00:00Z",
  },
  {
    id: 12,
    kind: "CREATE_RECORD",
    status: "PENDING",
    payload: { collectionId: 4, title: "Send certificates", values: { due_date: "2026-10-08" } },
    createdAt: "2026-10-08T12:00:00Z",
  },
  {
    id: 13,
    kind: "CREATE_COLLECTION",
    status: "PENDING",
    payload: { slug: "job_applications", name: "Job Applications", fields: [{ name: "Company" }] },
    createdAt: "2026-10-08T12:00:00Z",
  },
];

function actionsPage(items: unknown[]): (request: { url: URL }) => MockResponse {
  return ({ url }) => {
    expect(url.searchParams.get("group")).toBe("ACTIONS");
    return { body: { items, page: 0, size: 50, totalElements: items.length, totalPages: 1 } };
  };
}

describe("ProposalList", () => {
  it("lists the proposed actions with what each would do", async () => {
    mockApi({ "GET /api/proposals": actionsPage(actions), "GET /api/collections": { body: collections } });

    renderWithProviders(<ProposalList />);

    const interview = await screen.findByRole("article", { name: "Interview at Acme" });
    expect(await within(interview).findByText("Add to calendar")).toBeInTheDocument();
    expect(within(interview).getByText(/Acme HQ/)).toBeInTheDocument();
    expect(within(interview).getByText("Invitation to an interview on Friday")).toBeInTheDocument();
    expect(within(interview).getByText("95%")).toBeInTheDocument();
    expect(within(screen.getByRole("article", { name: "Send certificates" })).getByText("New to-do")).toBeInTheDocument();
    expect(within(screen.getByRole("article", { name: "Job Applications" })).getByText("New category")).toBeInTheDocument();
  });

  it("filters by what the proposal would do", async () => {
    mockApi({ "GET /api/proposals": actionsPage(actions), "GET /api/collections": { body: collections } });

    renderWithProviders(<ProposalList />);
    await screen.findByRole("article", { name: "Interview at Acme" });

    expect(screen.getByRole("button", { name: "All 3" })).toHaveAttribute("aria-pressed", "true");
    await userEvent.click(await screen.findByRole("button", { name: "To-dos 1" }));

    expect(screen.getAllByRole("article").map((card) => card.getAttribute("aria-label"))).toEqual(["Send certificates"]);
    await userEvent.click(screen.getByRole("button", { name: "All 3" }));
    expect(screen.getAllByRole("article")).toHaveLength(3);
  });

  it("approves and rejects", async () => {
    const calls = mockApi({
      "GET /api/proposals": actionsPage(actions),
      "GET /api/collections": { body: collections },
      "POST /api/proposals/11/decision": { body: { ...actions[0], status: "APPROVED" } },
      "POST /api/proposals/12/decision": { body: { ...actions[1], status: "REJECTED" } },
    });

    renderWithProviders(<ProposalList />);

    await userEvent.click(await screen.findByRole("button", { name: "Approve Interview at Acme" }));
    await userEvent.click(screen.getByRole("button", { name: "Reject Send certificates" }));

    await waitFor(() => {
      expect(calls.find((call) => call.path === "/api/proposals/11/decision")?.body).toEqual({ decision: "APPROVE" });
      expect(calls.find((call) => call.path === "/api/proposals/12/decision")?.body).toEqual({ decision: "REJECT" });
    });
  });

  it("says when there is nothing to do", async () => {
    mockApi({ "GET /api/proposals": actionsPage([]), "GET /api/collections": { body: collections } });

    renderWithProviders(<ProposalList />);

    expect(await screen.findByText("Nothing to review")).toBeInTheDocument();
  });
});
