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

const worksAt = { id: 3, slug: "works_at", name: "works at", inverseName: "employs", directed: true, attributesSchema: {}, createdBy: "USER" };

const acme = {
  id: 24,
  kind: "CREATE_RECORD",
  status: "PENDING",
  payload: { collectionId: 2, title: "Acme", values: { name: "Acme" } },
  createdAt: "2026-10-08T12:00:00Z",
};

function relation(id: number, from: unknown, to: unknown) {
  return { id, kind: "CREATE_RELATION", status: "PENDING", payload: { relationTypeId: 3, from, to }, createdAt: "2026-10-08T12:00:00Z" };
}

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

  it("lifts a card on hover and opens its details with every value", async () => {
    const calls = mockApi({
      "GET /api/proposals": findingsPage([{ ...findings[0], payload: { ...findings[0].payload, values: { ...findings[0].payload.values, phone: "+49 1", zip: "80331", city: "Munich" } } }]),
      "GET /api/collections": { body: collections },
      "POST /api/proposals/21/decision": { body: { ...findings[0], status: "APPROVED" } },
    });
    renderWithProviders(<FindingsList />);

    const anna = await screen.findByRole("article", { name: "Anna Berger" });
    expect(anna).toHaveClass("hover:shadow-md");
    expect(within(anna).queryByText("Munich")).not.toBeInTheDocument();
    await userEvent.click(within(anna).getByRole("button", { name: "Show details for Anna Berger" }));

    const sheet = await screen.findByRole("dialog", { name: "Anna Berger" });
    expect(within(sheet).getByText("Munich")).toBeInTheDocument();
    expect(within(sheet).getByText("Signature of the recruiter")).toBeInTheDocument();
    await userEvent.click(within(sheet).getByRole("button", { name: "Keep" }));

    await waitFor(() => expect(calls.find((call) => call.path === "/api/proposals/21/decision")?.body).toEqual({ decision: "APPROVE" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
  });

  it("shows the person's contact photo, how sure the assistant is, and lines the cards up", async () => {
    mockApi({
      "GET /api/proposals": findingsPage([{ ...findings[0], confidence: 0.87 }, findings[1]]),
      "GET /api/collections": { body: collections },
      "GET /api/senders/contact-photos": { body: [{ id: 9, emailAddress: "anna@acme.com" }] },
    });
    renderWithProviders(<FindingsList />);

    const anna = await screen.findByRole("article", { name: "Anna Berger" });
    await waitFor(() => expect(anna.querySelector("img")?.getAttribute("src")).toContain("/api/senders/contact-photos/9"));
    expect(within(anna).getByText("87%")).toBeInTheDocument();
    expect(anna).toHaveClass("h-full");
    // Without a picture: the initial of the title.
    expect(within(screen.getByRole("article", { name: "Software Engineer at Acme" })).getByText("S")).toBeInTheDocument();
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

  it("nests a relation under the record it starts from and keeps it disabled until both records are decided", async () => {
    mockApi({
      "GET /api/proposals": findingsPage([findings[0], acme, relation(25, { proposalId: 21 }, { proposalId: 24 })]),
      "GET /api/collections": { body: collections },
      "GET /api/relation-types": { body: [worksAt] },
    });

    renderWithProviders(<FindingsList />);

    const anna = await screen.findByRole("article", { name: "Anna Berger" });
    expect(await within(anna).findByText("works at Acme")).toBeInTheDocument();
    expect(within(anna).getByRole("button", { name: "Keep works at Acme" })).toBeDisabled();
    expect(within(screen.getByRole("article", { name: "Acme" })).queryByText("works at Acme")).not.toBeInTheDocument();
    expect(screen.getAllByRole("article")).toHaveLength(2);
    expect(screen.getByRole("button", { name: "All 2" })).toBeInTheDocument();
  });

  it("names the other end by the title of an existing record and lets you keep the relation", async () => {
    const calls = mockApi({
      "GET /api/proposals": findingsPage([findings[0], relation(26, { proposalId: 21 }, { collectionId: 2, recordId: 9 })]),
      "GET /api/collections": { body: collections },
      "GET /api/relation-types": { body: [worksAt] },
      "GET /api/collections/2/records/9": { body: { id: 9, collectionId: 2, title: "Globex", values: {}, extra: {}, createdAt: "2026-01-01T00:00:00Z", createdBy: "AI" } },
      "POST /api/proposals/26/decision": { body: relation(26, {}, {}) },
    });

    renderWithProviders(<FindingsList />);

    const anna = await screen.findByRole("article", { name: "Anna Berger" });
    expect(await within(anna).findByText("works at Globex")).toBeInTheDocument();
    // the person is still pending, so the relation cannot be kept yet
    expect(within(anna).getByRole("button", { name: "Keep works at Globex" })).toBeDisabled();
    await userEvent.click(within(anna).getByRole("button", { name: "Dismiss works at Globex" }));

    await waitFor(() => expect(calls.find((call) => call.path === "/api/proposals/26/decision")?.body).toEqual({ decision: "REJECT" }));
  });

  it("lets you keep a relation between two records that already exist", async () => {
    const calls = mockApi({
      "GET /api/proposals": findingsPage([relation(27, { collectionId: 1, recordId: 4 }, { collectionId: 2, recordId: 9 })]),
      "GET /api/collections": { body: collections },
      "GET /api/relation-types": { body: [worksAt] },
      "GET /api/collections/1/records/4": { body: { id: 4, collectionId: 1, title: "Bob Stone", values: {}, extra: {}, createdAt: "2026-01-01T00:00:00Z", createdBy: "USER" } },
      "GET /api/collections/2/records/9": { body: { id: 9, collectionId: 2, title: "Globex", values: {}, extra: {}, createdAt: "2026-01-01T00:00:00Z", createdBy: "AI" } },
      "POST /api/proposals/27/decision": { body: relation(27, {}, {}) },
    });

    renderWithProviders(<FindingsList />);

    const connections = await screen.findByRole("list", { name: "New connections: works at" });
    expect(await within(connections).findByRole("listitem", { name: "Bob Stone works at Globex" })).toBeInTheDocument();
    await userEvent.click(within(connections).getByRole("button", { name: "Keep Bob Stone works at Globex" }));

    await waitFor(() => expect(calls.find((call) => call.path === "/api/proposals/27/decision")?.body).toEqual({ decision: "APPROVE" }));
  });

  it("groups connections by kind, counts them and keeps a whole group at once", async () => {
    const record = (collectionId: number, id: number, title: string) =>
      ({ body: { id, collectionId, title, values: {}, extra: {}, createdAt: "2026-01-01T00:00:00Z", createdBy: "USER" } });
    const calls = mockApi({
      "GET /api/proposals": findingsPage([
        findings[0],
        relation(27, { collectionId: 1, recordId: 4 }, { collectionId: 2, recordId: 9 }),
        relation(28, { collectionId: 1, recordId: 5 }, { collectionId: 2, recordId: 9 }),
      ]),
      "GET /api/collections": { body: collections },
      "GET /api/relation-types": { body: [worksAt] },
      "GET /api/collections/1/records/4": record(1, 4, "Bob Stone"),
      "GET /api/collections/1/records/5": record(1, 5, "Carla Diaz"),
      "GET /api/collections/2/records/9": record(2, 9, "Globex"),
      "POST /api/proposals/27/decision": { body: relation(27, {}, {}) },
      "POST /api/proposals/28/decision": { body: relation(28, {}, {}) },
    });

    renderWithProviders(<FindingsList />);

    expect(await screen.findByRole("button", { name: "All 3" })).toBeInTheDocument();
    await userEvent.click(await screen.findByRole("button", { name: "Connections 2" }));
    expect(screen.queryByRole("article", { name: "Anna Berger" })).not.toBeInTheDocument();

    const group = await screen.findByRole("list", { name: "New connections: works at" });
    expect(await within(group).findByRole("listitem", { name: "Carla Diaz works at Globex" })).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Keep all 2 works at" }));

    await waitFor(() => {
      expect(calls.find((call) => call.path === "/api/proposals/27/decision")?.body).toEqual({ decision: "APPROVE" });
      expect(calls.find((call) => call.path === "/api/proposals/28/decision")?.body).toEqual({ decision: "APPROVE" });
    });
  });
});
