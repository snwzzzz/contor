import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { mockApi } from "@/test/api-mock";
import { resetNavigation, router } from "@/test/next-navigation";
import { renderWithProviders } from "@/test/render";
import { CollectionReports } from "./CollectionReports";

vi.mock("next/navigation", () => import("@/test/next-navigation"));

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

async function openSheet(name = "Customers") {
  await userEvent.click(screen.getByRole("button", { name: /^Reports/ }));
  return screen.findByRole("dialog", { name: `Reports on ${name}` });
}

describe("CollectionReports", () => {
  beforeEach(() => resetNavigation("/collections/1"));

  it("is a toolbar button with the number of reports; the reports open in a side sheet as plain rows", async () => {
    mockApi({
      "GET /api/ai/status": { body: READY },
      "GET /api/collections/1/reports": { body: [done] },
    });
    renderWithProviders(<CollectionReports collectionId={1} collectionName="Customers" filters={[]} search="" />);

    await waitFor(() => expect(screen.getByRole("button", { name: /^Reports/ })).toHaveTextContent("1"));
    const sheet = await openSheet();

    const row = within(sheet).getByRole("link", { name: /Top customers/ });
    expect(row).toHaveAttribute("href", "/collections/1/reports/6");
    expect(row).not.toHaveClass("border");
  });

  it("shows when a collection report is still being written", async () => {
    mockApi({
      "GET /api/ai/status": { body: READY },
      "GET /api/collections/1/reports": { body: [{ ...done, state: "COLLECTING", summary: undefined }] },
    });
    renderWithProviders(<CollectionReports collectionId={1} collectionName="Customers" filters={[]} search="" />);

    const sheet = await openSheet();
    expect(await within(sheet).findByText("Writing…")).toBeInTheDocument();
  });

  it("asks about the records the table shows and opens the new report, where the assistant writes it", async () => {
    const calls = mockApi({
      "GET /api/ai/status": { body: READY },
      "GET /api/collections/1/reports": { body: [] },
      "POST /api/collections/1/reports": { status: 202, body: { ...done, id: 9, state: "QUEUED" } },
    });
    const user = userEvent.setup();
    renderWithProviders(
      <CollectionReports collectionId={1} collectionName="Sales orders" filters={["state:eq:delivered"]} search="acme" matching={12} />,
    );
    const sheet = await openSheet("Sales orders");

    expect(within(sheet).getByText("Covers the 12 records the table shows (filters and search).")).toBeInTheDocument();
    await user.type(within(sheet).getByRole("textbox", { name: "Ask about Sales orders" }), "Top 5 customers?{Enter}");

    await waitFor(() => expect(router.push).toHaveBeenCalledWith("/collections/1/reports/9"));
    expect(calls.find((call) => call.method === "POST")?.body).toEqual({
      prompt: "Top 5 customers?", filters: ["state:eq:delivered"], search: "acme",
    });
  });

  it("asks about the whole collection without filters, from a suggestion", async () => {
    const calls = mockApi({
      "GET /api/ai/status": { body: READY },
      "GET /api/collections/1/reports": { body: [] },
      "POST /api/collections/1/reports": { status: 202, body: { ...done, state: "QUEUED" } },
    });
    const user = userEvent.setup();
    renderWithProviders(<CollectionReports collectionId={1} collectionName="Customers" filters={[]} search="" />);
    const sheet = await openSheet();

    await user.click(within(sheet).getByRole("button", { name: "Top 5 by count" }));
    await user.click(within(sheet).getByRole("button", { name: "Create report" }));

    await waitFor(() => expect(calls.find((call) => call.method === "POST")?.body).toEqual({ prompt: "Top 5 by count" }));
    expect(within(sheet).queryByText(/Covers the/)).not.toBeInTheDocument();
  });

  it("says when there is no report yet", async () => {
    mockApi({
      "GET /api/ai/status": { body: READY },
      "GET /api/collections/1/reports": { body: [] },
    });
    renderWithProviders(<CollectionReports collectionId={1} collectionName="Customers" filters={[]} search="" />);

    const sheet = await openSheet();
    expect(await within(sheet).findByText("No reports yet")).toBeInTheDocument();
  });
});
