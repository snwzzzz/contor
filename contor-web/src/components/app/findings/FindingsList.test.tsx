import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { mockApi, type MockResponse } from "@/test/api-mock";
import { renderWithProviders } from "@/test/render";
import { FindingsList } from "./FindingsList";

function collection(id: number, slug: string, name: string) {
  return { id, slug, name, system: id < 3, matchKeys: [], displayConfig: {}, schemaVersion: 1, createdBy: "USER", fields: [] };
}

const collections = [collection(1, "people", "People"), collection(2, "organizations", "Organizations"), collection(7, "job_applications", "Job Applications")];

const findings = [
  {
    id: 21,
    kind: "CREATE_RECORD",
    status: "PENDING",
    payload: { collectionId: 1, title: "Anna Berger", values: { full_name: "Anna Berger", email: "anna@acme.com", role_title: "Recruiting" } },
    reasoning: "Signature of the recruiter",
    createdAt: "2026-10-08T12:00:00Z",
  },
  {
    id: 22,
    kind: "CREATE_RECORD",
    status: "PENDING",
    payload: { collectionId: 7, title: "Software Engineer at Acme", values: { company_name: "Acme GmbH", status: "interview" } },
    createdAt: "2026-10-08T12:00:00Z",
  },
  {
    id: 23,
    kind: "UPDATE_RECORD",
    status: "PENDING",
    payload: { collectionId: 1, recordId: 4, values: { phone: "+49 89 1234567" } },
    createdAt: "2026-10-08T12:00:00Z",
  },
];

function findingsPage(items: unknown[]): (request: { url: URL }) => MockResponse {
  return ({ url }) => {
    expect(url.searchParams.get("group")).toBe("FINDINGS");
    return { body: { items, page: 0, size: 60, totalElements: items.length, totalPages: 1 } };
  };
}

describe("FindingsList", () => {
  it("shows each finding as a card colored by its collection", async () => {
    mockApi({ "GET /api/proposals": findingsPage(findings), "GET /api/collections": { body: collections } });

    renderWithProviders(<FindingsList />);

    const anna = await screen.findByRole("article", { name: "Anna Berger" });
    expect(anna).toHaveAttribute("data-collection", "1");
    expect(await within(anna).findByText("People")).toBeInTheDocument();
    expect(within(anna).getByText("Email")).toBeInTheDocument();
    expect(within(anna).getByText("anna@acme.com")).toBeInTheDocument();
    expect(within(anna).getByText("New")).toBeInTheDocument();
    expect(within(screen.getByRole("article", { name: "Record #4" })).getByText("Update")).toBeInTheDocument();
  });

  it("filters by collection", async () => {
    mockApi({ "GET /api/proposals": findingsPage(findings), "GET /api/collections": { body: collections } });

    renderWithProviders(<FindingsList />);
    await screen.findByRole("article", { name: "Anna Berger" });

    await userEvent.click(await screen.findByRole("button", { name: "Job Applications 1" }));

    expect(screen.getAllByRole("article").map((card) => card.getAttribute("aria-label"))).toEqual(["Software Engineer at Acme"]);
    await userEvent.click(screen.getByRole("button", { name: "All 3" }));
    expect(screen.getAllByRole("article")).toHaveLength(3);
  });

  it("keeps or dismisses a finding", async () => {
    const calls = mockApi({
      "GET /api/proposals": findingsPage(findings),
      "GET /api/collections": { body: collections },
      "POST /api/proposals/21/decision": { body: { ...findings[0], status: "APPROVED" } },
      "POST /api/proposals/22/decision": { body: { ...findings[1], status: "REJECTED" } },
    });

    renderWithProviders(<FindingsList />);

    await userEvent.click(await screen.findByRole("button", { name: "Keep Anna Berger" }));
    await userEvent.click(screen.getByRole("button", { name: "Dismiss Software Engineer at Acme" }));

    await waitFor(() => {
      expect(calls.find((call) => call.path === "/api/proposals/21/decision")?.body).toEqual({ decision: "APPROVE" });
      expect(calls.find((call) => call.path === "/api/proposals/22/decision")?.body).toEqual({ decision: "REJECT" });
    });
  });

  it("says when nothing was found", async () => {
    mockApi({ "GET /api/proposals": findingsPage([]), "GET /api/collections": { body: collections } });

    renderWithProviders(<FindingsList />);

    expect(await screen.findByText("No new findings")).toBeInTheDocument();
  });
});
