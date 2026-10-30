import { screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { apiError, mockApi } from "@/test/api-mock";
import { resetNavigation } from "@/test/next-navigation";
import { renderWithProviders } from "@/test/render";
import { ProcessingIndicator } from "./ProcessingIndicator";

vi.mock("next/navigation", () => import("@/test/next-navigation"));

const settings = { mailAccess: true, historyDays: 90, categories: "SUGGEST", records: "SUGGEST", calendar: "SUGGEST", tasks: "SUGGEST", replies: "OFF" };

describe("ProcessingIndicator", () => {
  beforeEach(() => resetNavigation("/inbox"));

  it("invites to set up the AI until the setup is done", async () => {
    mockApi({ "GET /api/ai/settings": { body: settings }, "GET /api/ai/analysis/latest": apiError(404, "none") });

    renderWithProviders(<ProcessingIndicator />);

    expect(await screen.findByRole("link", { name: "Set up AI" })).toHaveAttribute("href", "/inbox?ai-setup=1");
  });

  it("shows the progress of a running analysis, linking to Settings → Autonomy", async () => {
    mockApi({
      "GET /api/ai/settings": { body: { ...settings, onboardedAt: "2026-10-08T12:00:00Z" } },
      "GET /api/ai/analysis/latest": {
        body: { id: 10, status: "EXTRACTING", totalEmails: 100, processedEmails: 25, failedEmails: 0, proposalsCreated: 0,
          autoApplied: 0, categoriesDiscovered: 0, createdAt: "2026-10-08T12:00:00Z" },
      },
    });

    renderWithProviders(<ProcessingIndicator />);

    expect(await screen.findByRole("link", { name: "AI analyzing 25/100" })).toHaveAttribute("href", "/inbox?settings=autonomy");
  });

  it("is idle after a finished run", async () => {
    mockApi({
      "GET /api/ai/settings": { body: { ...settings, onboardedAt: "2026-10-08T12:00:00Z" } },
      "GET /api/ai/analysis/latest": {
        body: { id: 10, status: "COMPLETED", totalEmails: 100, processedEmails: 100, failedEmails: 0, proposalsCreated: 0,
          autoApplied: 0, categoriesDiscovered: 0, createdAt: "2026-10-08T12:00:00Z" },
      },
    });

    renderWithProviders(<ProcessingIndicator />);

    expect(await screen.findByText("AI active")).toBeInTheDocument();
  });
});
