import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { mockApi } from "@/test/api-mock";
import { renderWithProviders } from "@/test/render";
import { RecordReports } from "./RecordReports";

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
  it("lists the record's reports with their summary, fact check and a link to each", async () => {
    mockApi({
      "GET /api/ai/status": { body: READY },
      "GET /api/collections/1/records/4711/reports": { body: [done] },
    });

    renderWithProviders(<RecordReports collectionId={1} recordId={4711} />);

    const link = await screen.findByRole("link", { name: /Order 4711 review/ });
    expect(link).toHaveAttribute("href", "/collections/1/records/4711/reports/5");
    expect(screen.getByText("The order was delivered five days late.")).toBeInTheDocument();
    expect(screen.getByText("3 of 4 sentences with a source")).toBeInTheDocument();
    expect(screen.getByText("1 follow-up")).toBeInTheDocument();
  });

  it("shows what the assistant is doing while a report is generated", async () => {
    mockApi({
      "GET /api/ai/status": { body: READY },
      "GET /api/collections/1/records/4711/reports": { body: [{ ...done, state: "DRAFTING", summary: undefined }] },
    });

    renderWithProviders(<RecordReports collectionId={1} recordId={4711} />);

    expect(await screen.findByText("Writing the report…")).toBeInTheDocument();
  });

  it("asks for a report with the typed request on Enter", async () => {
    const calls = mockApi({
      "GET /api/ai/status": { body: READY },
      "GET /api/collections/1/records/4711/reports": { body: [] },
      "POST /api/collections/1/records/4711/reports": { status: 202, body: { ...done, state: "QUEUED" } },
    });
    const user = userEvent.setup();
    renderWithProviders(<RecordReports collectionId={1} recordId={4711} />);

    const prompt = await screen.findByRole("textbox", { name: "Ask about this record" });
    await user.type(prompt, "What went wrong?{Enter}");

    await waitFor(() => expect(calls.some((call) => call.method === "POST")).toBe(true));
    expect(calls.find((call) => call.method === "POST")?.body).toEqual({ prompt: "What went wrong?" });
    await waitFor(() => expect(prompt).toHaveValue(""));
  });

  it("fills the request from a suggestion", async () => {
    mockApi({
      "GET /api/ai/status": { body: READY },
      "GET /api/collections/1/records/4711/reports": { body: [] },
    });
    const user = userEvent.setup();
    renderWithProviders(<RecordReports collectionId={1} recordId={4711} />);

    await user.click(await screen.findByRole("button", { name: "What went wrong?" }));

    expect(screen.getByRole("textbox", { name: "Ask about this record" })).toHaveValue("What went wrong?");
  });

  it("cannot ask while the model is not ready", async () => {
    mockApi({
      "GET /api/ai/status": { body: { reachable: false, ready: false, models: [] } },
      "GET /api/collections/1/records/4711/reports": { body: [] },
    });

    renderWithProviders(<RecordReports collectionId={1} recordId={4711} />);

    expect(await screen.findByText(/The AI model is not ready/)).toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: "Ask about this record" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Create report" })).toBeDisabled();
  });
});
