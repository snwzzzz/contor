import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { mockApi } from "@/test/api-mock";
import { renderWithProviders } from "@/test/render";
import { CollectionReports } from "./CollectionReports";

const READY = { reachable: true, ready: true, models: [] };

const done = {
  id: 6,
  title: "Top customers",
  prompt: "Top 5 best selling customers",
  state: "READY",
  summary: "Kessler GmbH bought the most.",
  claimsTotal: 6,
  claimsVerified: 6,
  followUpCount: 0,
  createdAt: "2026-10-15T10:00:00Z",
};

describe("CollectionReports", () => {
  it("lists the collection's reports with a link to each", async () => {
    mockApi({
      "GET /api/ai/status": { body: READY },
      "GET /api/collections/1/reports": { body: [done] },
    });

    renderWithProviders(<CollectionReports collectionId={1} collectionName="Customers" filters={[]} search="" />);

    const link = await screen.findByRole("link", { name: /Top customers/ });
    expect(link).toHaveAttribute("href", "/collections/1/reports/6");
    expect(screen.getByText("Kessler GmbH bought the most.")).toBeInTheDocument();
  });

  it("shows the figures step while a collection report is computed", async () => {
    mockApi({
      "GET /api/ai/status": { body: READY },
      "GET /api/collections/1/reports": { body: [{ ...done, state: "COLLECTING", summary: undefined }] },
    });

    renderWithProviders(<CollectionReports collectionId={1} collectionName="Customers" filters={[]} search="" />);

    expect(await screen.findByText("Choosing and computing the figures…")).toBeInTheDocument();
  });

  it("asks about the records the table shows: its filters and search go with the request", async () => {
    const calls = mockApi({
      "GET /api/ai/status": { body: READY },
      "GET /api/collections/1/reports": { body: [] },
      "POST /api/collections/1/reports": { status: 202, body: { ...done, state: "QUEUED" } },
    });
    const user = userEvent.setup();
    renderWithProviders(
      <CollectionReports collectionId={1} collectionName="Sales orders" filters={["state:eq:delivered"]} search="acme" matching={12} />,
    );

    expect(await screen.findByText("Covers the 12 records the table shows (filters and search).")).toBeInTheDocument();
    await user.type(screen.getByRole("textbox", { name: "Ask about Sales orders" }), "Top 5 customers?{Enter}");

    await waitFor(() => expect(calls.some((call) => call.method === "POST")).toBe(true));
    expect(calls.find((call) => call.method === "POST")?.body).toEqual({
      prompt: "Top 5 customers?", filters: ["state:eq:delivered"], search: "acme",
    });
  });

  it("asks about the whole collection without filters", async () => {
    const calls = mockApi({
      "GET /api/ai/status": { body: READY },
      "GET /api/collections/1/reports": { body: [] },
      "POST /api/collections/1/reports": { status: 202, body: { ...done, state: "QUEUED" } },
    });
    const user = userEvent.setup();
    renderWithProviders(<CollectionReports collectionId={1} collectionName="Customers" filters={[]} search="" />);

    await user.click(await screen.findByRole("button", { name: "Top 5 by count" }));
    await user.click(screen.getByRole("button", { name: "Create report" }));

    await waitFor(() => expect(calls.some((call) => call.method === "POST")).toBe(true));
    expect(calls.find((call) => call.method === "POST")?.body).toEqual({ prompt: "Top 5 by count" });
    expect(screen.queryByText(/Covers the/)).not.toBeInTheDocument();
  });

  it("shows only the newest reports until asked for all", async () => {
    mockApi({
      "GET /api/ai/status": { body: READY },
      "GET /api/collections/1/reports": { body: [1, 2, 3, 4].map((id) => ({ ...done, id, title: `Report ${id}` })) },
    });
    const user = userEvent.setup();
    renderWithProviders(<CollectionReports collectionId={1} collectionName="Customers" filters={[]} search="" />);

    expect(await screen.findByRole("link", { name: /Report 3/ })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /Report 4/ })).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Show all 4 reports" }));
    expect(screen.getByRole("link", { name: /Report 4/ })).toBeInTheDocument();
  });
});
