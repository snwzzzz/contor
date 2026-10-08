import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { mockApi } from "@/test/api-mock";
import { renderWithProviders } from "@/test/render";
import { AnalysisProgress } from "./AnalysisProgress";

function run(status: string, extra: Record<string, unknown> = {}) {
  return {
    id: 10,
    status,
    totalEmails: 100,
    processedEmails: 25,
    failedEmails: 1,
    proposalsCreated: 7,
    autoApplied: 3,
    categoriesDiscovered: 2,
    createdAt: "2026-10-08T12:00:00Z",
    ...extra,
  };
}

const jobProposal = {
  id: 5,
  kind: "CREATE_COLLECTION",
  status: "PENDING",
  payload: {
    slug: "job_applications",
    name: "Job applications",
    description: "Jobs you applied for",
    fields: [
      { slug: "company", name: "Company", type: "TEXT" },
      { slug: "stage", name: "Stage", type: "STATUS" },
    ],
  },
  reasoning: "Several application confirmations",
  createdAt: "2026-10-08T12:00:00Z",
};

const recordProposal = { ...jobProposal, id: 6, kind: "CREATE_RECORD", payload: { collectionId: 1, title: "Anna" } };

describe("AnalysisProgress", () => {
  it("shows progress and counters while extracting, and cancels", async () => {
    const calls = mockApi({
      "GET /api/ai/analysis/latest": { body: run("EXTRACTING") },
      "POST /api/ai/analysis/10/cancel": { body: run("CANCELLED") },
    });

    renderWithProviders(<AnalysisProgress />);

    expect(await screen.findByRole("progressbar", { name: "Emails analyzed" })).toHaveAttribute("aria-valuenow", "25");
    expect(screen.getByText("25 of 100 emails")).toBeInTheDocument();
    expect(screen.getByText("7")).toBeInTheDocument();
    expect(screen.getByText("3")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Stop analysis" }));

    await waitFor(() => expect(calls.some((call) => call.method === "POST" && call.path === "/api/ai/analysis/10/cancel")).toBe(true));
  });

  it("lets the user review discovered categories and continue", async () => {
    const calls = mockApi({
      "GET /api/ai/analysis/latest": { body: run("AWAITING_REVIEW", { processedEmails: 0, totalEmails: 0 }) },
      "GET /api/proposals": { body: { items: [jobProposal, recordProposal], page: 0, size: 50, totalElements: 2, totalPages: 1 } },
      "POST /api/proposals/5/decision": { body: { ...jobProposal, status: "APPROVED" } },
      "POST /api/ai/analysis/10/continue": { body: run("EXTRACTING") },
    });

    renderWithProviders(<AnalysisProgress />);

    expect(await screen.findByText("Job applications")).toBeInTheDocument();
    expect(screen.getByText("Company, Stage")).toBeInTheDocument();
    expect(screen.getByText("Several application confirmations")).toBeInTheDocument();
    expect(screen.queryByText("Anna")).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Create Job applications" }));
    await waitFor(() => {
      const decision = calls.find((call) => call.path === "/api/proposals/5/decision");
      expect(decision?.body).toEqual({ decision: "APPROVE" });
    });

    await userEvent.click(screen.getByRole("button", { name: "Continue analysis" }));
    await waitFor(() => expect(calls.some((call) => call.path === "/api/ai/analysis/10/continue")).toBe(true));
  });

  it("summarizes a finished run with links to the results", async () => {
    mockApi({ "GET /api/ai/analysis/latest": { body: run("COMPLETED", { processedEmails: 100 }) } });

    renderWithProviders(<AnalysisProgress />);

    expect(await screen.findByText(/analysis finished/i)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /review proposals/i })).toHaveAttribute("href", "/proposals");
    expect(screen.getByRole("link", { name: /calendar/i })).toHaveAttribute("href", "/calendar");
    expect(screen.getByRole("link", { name: /to-dos/i })).toHaveAttribute("href", "/tasks");
  });

  it("shows why a run failed", async () => {
    mockApi({ "GET /api/ai/analysis/latest": { body: run("FAILED", { error: "Ollama is not reachable" }) } });

    renderWithProviders(<AnalysisProgress />);

    expect(await screen.findByRole("alert")).toHaveTextContent("Ollama is not reachable");
  });
});
