import { screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { apiError, mockApi } from "@/test/api-mock";
import { resetNavigation } from "@/test/next-navigation";
import { renderWithProviders } from "@/test/render";
import { HomeHero } from "./HomeHero";

vi.mock("next/navigation", () => import("@/test/next-navigation"));

const me = { id: 1, username: "anna", email: "anna@example.com", role: "OWNER", totpEnabled: false };

function run(extra: Record<string, unknown> = {}) {
  return {
    id: 1, status: "EXTRACTING", discoverySteps: 2, discoveryStepsDone: 2, totalEmails: 229, processedEmails: 224, failedEmails: 0,
    proposalsCreated: 3, autoApplied: 0, categoriesDiscovered: 4, createdAt: "2026-10-19T06:00:00Z", ...extra,
  };
}

function api(latest: ReturnType<typeof apiError> | { body: unknown }, pending: number) {
  return mockApi({
    "GET /api/auth/me": { body: me },
    "GET /api/ai/analysis/latest": latest,
    "GET /api/proposals/count": { body: { count: pending } },
  });
}

describe("HomeHero", () => {
  beforeEach(() => {
    resetNavigation("/");
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date(2026, 9, 19, 8, 0));
  });
  afterEach(() => vi.useRealTimers());

  it("greets the user by name for the time of day", async () => {
    api(apiError(404, "No run"), 0);
    renderWithProviders(<HomeHero />);

    expect(await screen.findByRole("heading", { level: 1, name: "Good morning, anna" })).toBeInTheDocument();
  });

  it("greets in the afternoon too", async () => {
    vi.setSystemTime(new Date(2026, 9, 19, 15, 0));
    api(apiError(404, "No run"), 0);
    renderWithProviders(<HomeHero />);

    expect(await screen.findByRole("heading", { level: 1, name: "Good afternoon, anna" })).toBeInTheDocument();
  });

  it("shows a running analysis with its progress", async () => {
    api({ body: run() }, 5);
    renderWithProviders(<HomeHero />);

    expect(await screen.findByText("AI is analyzing 224 of 229 emails")).toBeInTheDocument();
    expect(screen.getByRole("progressbar", { name: "Analysis progress" })).toHaveAttribute("aria-valuenow", "98");
    expect(screen.getByRole("link", { name: "View progress" })).toHaveAttribute("href", "/?ai-setup=1");
  });

  it("invites to review waiting proposals", async () => {
    api({ body: run({ status: "COMPLETED" }) }, 5);
    renderWithProviders(<HomeHero />);

    expect(await screen.findByText("5 proposals waiting for review")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Review now" })).toHaveAttribute("href", "/proposals");
  });

  it("says when everything is done", async () => {
    api(apiError(404, "No run"), 0);
    renderWithProviders(<HomeHero />);

    expect(await screen.findByText("All caught up")).toBeInTheDocument();
  });
});
