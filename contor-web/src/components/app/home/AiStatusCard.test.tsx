import { screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { apiError, mockApi } from "@/test/api-mock";
import { resetNavigation } from "@/test/next-navigation";
import { renderWithProviders } from "@/test/render";
import { AiStatusCard } from "./AiStatusCard";

vi.mock("next/navigation", () => import("@/test/next-navigation"));

const status = {
  reachable: true,
  ready: true,
  models: [
    { name: "qwen3.5:9b", roles: ["EXTRACTION", "ROUTING"], installed: true, pullState: "IDLE" },
    { name: "embeddinggemma", roles: ["EMBEDDING"], installed: true, pullState: "IDLE" },
  ],
};

function run(extra: Record<string, unknown>) {
  return {
    id: 1, status: "COMPLETED", discoverySteps: 2, discoveryStepsDone: 2, totalEmails: 229, processedEmails: 229, failedEmails: 0,
    proposalsCreated: 3, autoApplied: 0, categoriesDiscovered: 4, createdAt: "2026-10-19T06:00:00Z", ...extra,
  };
}

describe("AiStatusCard", () => {
  beforeEach(() => {
    resetNavigation("/");
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date("2026-10-19T12:00:00Z"));
  });
  afterEach(() => vi.useRealTimers());

  it("shows the model, the last run and a link to the AI settings", async () => {
    mockApi({ "GET /api/ai/status": { body: status }, "GET /api/ai/analysis/latest": { body: run({ finishedAt: "2026-10-19T09:00:00Z" }) } });
    renderWithProviders(<AiStatusCard />);

    expect(await screen.findByText("qwen3.5:9b")).toBeInTheDocument();
    expect(screen.getByText("Ready")).toBeInTheDocument();
    expect(await screen.findByText("229 emails · 3 hr. ago")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "AI settings" })).toHaveAttribute("href", "/?settings=ai");
  });

  it("shows the queue of a running analysis", async () => {
    mockApi({ "GET /api/ai/status": { body: status }, "GET /api/ai/analysis/latest": { body: run({ status: "EXTRACTING", processedEmails: 100, totalEmails: 200 }) } });
    renderWithProviders(<AiStatusCard />);

    expect(await screen.findByRole("progressbar", { name: "Analysis queue" })).toHaveAttribute("aria-valuenow", "50");
    expect(screen.getByText("100 of 200 emails")).toBeInTheDocument();
  });

  it("says when Ollama cannot be reached", async () => {
    mockApi({ "GET /api/ai/status": { body: { reachable: false, ready: false, models: [] } }, "GET /api/ai/analysis/latest": apiError(404, "No run") });
    renderWithProviders(<AiStatusCard />);

    expect(await screen.findByText("Ollama not reachable")).toBeInTheDocument();
    expect(screen.getByText("No analysis yet")).toBeInTheDocument();
  });
});
