import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { mockApi } from "@/test/api-mock";
import { resetNavigation, router } from "@/test/next-navigation";
import { renderWithProviders } from "@/test/render";
import { RecordReports } from "./RecordReports";

vi.mock("next/navigation", () => import("@/test/next-navigation"));

const READY = { reachable: true, ready: true, models: [] };

const done = {
  id: 5,
  title: "Order 4711 review",
  prompt: "What went wrong?",
  state: "READY",
  summary: "The order was delivered five days late.",
  claimsTotal: 4,
  claimsVerified: 3,
  followUpCount: 1,
  createdAt: "2026-10-14T10:00:00Z",
};

describe("RecordReports", () => {
  beforeEach(() => resetNavigation("/collections/1/records/4711"));

  it("asks one question with one input, the reports listed below as plain rows (no boxes)", async () => {
    mockApi({
      "GET /api/ai/status": { body: READY },
      "GET /api/collections/1/records/4711/reports": { body: [done] },
    });

    renderWithProviders(<RecordReports collectionId={1} recordId={4711} recordTitle="Anna Berger" />);

    expect(screen.getByRole("heading", { name: "What do you want to know about Anna Berger?" })).toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: "Ask about this record" })).toBeInTheDocument();
    // Blends in: the app's normal field look (not a pill), left-aligned like the other sections, the list under a section header.
    const form = screen.getByRole("textbox", { name: "Ask about this record" }).closest("form") as HTMLElement;
    expect(form).toHaveClass("rounded-md");
    expect(form).not.toHaveClass("rounded-3xl");
    expect(screen.getByRole("region", { name: "Reports" })).not.toHaveClass("mx-auto");
    expect(await screen.findByRole("heading", { level: 2, name: "Reports" })).toBeInTheDocument();

    const list = await screen.findByRole("list", { name: "Reports on this record" });
    const row = within(list).getByRole("link", { name: /Order 4711 review/ });
    expect(row).toHaveAttribute("href", "/collections/1/records/4711/reports/5");
    expect(row).not.toHaveClass("border");
    expect(within(row).getByText("14 Oct")).toBeInTheDocument();
  });

  it("shows when a report is still being written", async () => {
    mockApi({
      "GET /api/ai/status": { body: READY },
      "GET /api/collections/1/records/4711/reports": { body: [{ ...done, state: "DRAFTING", summary: undefined }] },
    });

    renderWithProviders(<RecordReports collectionId={1} recordId={4711} recordTitle="Anna Berger" />);

    expect(await screen.findByText("Writing…")).toBeInTheDocument();
  });

  it("has no list while there is no report yet, only the input and the suggestions", async () => {
    mockApi({
      "GET /api/ai/status": { body: READY },
      "GET /api/collections/1/records/4711/reports": { body: [] },
    });

    renderWithProviders(<RecordReports collectionId={1} recordId={4711} recordTitle="Anna Berger" />);

    expect(await screen.findByRole("button", { name: "Summarize this record" })).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByRole("list", { name: "Reports on this record" })).not.toBeInTheDocument());
  });

  it("sends the request on Enter and goes straight to the new report, where the assistant works on it", async () => {
    const calls = mockApi({
      "GET /api/ai/status": { body: READY },
      "GET /api/collections/1/records/4711/reports": { body: [] },
      "POST /api/collections/1/records/4711/reports": { status: 202, body: { ...done, id: 12, state: "QUEUED" } },
    });
    const user = userEvent.setup();
    renderWithProviders(<RecordReports collectionId={1} recordId={4711} recordTitle="Anna Berger" />);

    await user.type(screen.getByRole("textbox", { name: "Ask about this record" }), "What went wrong?{Enter}");

    await waitFor(() => expect(router.push).toHaveBeenCalledWith("/collections/1/records/4711/reports/12"));
    expect(calls.find((call) => call.method === "POST")?.body).toEqual({ prompt: "What went wrong?" });
  });

  it("sends a suggestion right away", async () => {
    const calls = mockApi({
      "GET /api/ai/status": { body: READY },
      "GET /api/collections/1/records/4711/reports": { body: [] },
      "POST /api/collections/1/records/4711/reports": { status: 202, body: { ...done, id: 13, state: "QUEUED" } },
    });
    const user = userEvent.setup();
    renderWithProviders(<RecordReports collectionId={1} recordId={4711} recordTitle="Anna Berger" />);

    await user.click(await screen.findByRole("button", { name: "What went wrong?" }));

    await waitFor(() => expect(router.push).toHaveBeenCalledWith("/collections/1/records/4711/reports/13"));
    expect(calls.find((call) => call.method === "POST")?.body).toEqual({ prompt: "What went wrong?" });
  });

  it("cannot ask while the model is not ready", async () => {
    mockApi({
      "GET /api/ai/status": { body: { reachable: false, ready: false, models: [] } },
      "GET /api/collections/1/records/4711/reports": { body: [] },
    });

    renderWithProviders(<RecordReports collectionId={1} recordId={4711} recordTitle="Anna Berger" />);

    expect(await screen.findByText(/The AI model is not ready/)).toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: "Ask about this record" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Create report" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "What went wrong?" })).toBeDisabled();
  });
});
