import { screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { mockApi } from "@/test/api-mock";
import { resetNavigation } from "@/test/next-navigation";
import { renderWithProviders } from "@/test/render";
import { AiSettingsPanel } from "./AiSettingsPanel";

vi.mock("next/navigation", () => import("@/test/next-navigation"));

describe("AiSettingsPanel", () => {
  beforeEach(() => resetNavigation("/inbox"));

  it("only manages the models: no setup and no inbox analysis (both live in Autonomy)", async () => {
    mockApi({
      "GET /api/ai/settings": {
        body: {
          mailAccess: true,
          historyDays: 90,
          categories: "SUGGEST",
          records: "SUGGEST",
          calendar: "SUGGEST",
          tasks: "SUGGEST",
          replies: "OFF",
          courtesyReplies: false,
          onboardedAt: "2026-10-01T10:00:00Z",
        },
      },
      "GET /api/ai/status": { body: { reachable: true, version: "0.40.1", ready: true, models: [] } },
    });
    renderWithProviders(<AiSettingsPanel />);

    expect(screen.getByRole("heading", { name: "Local model" })).toBeInTheDocument();
    expect(await screen.findByText("Ollama 0.40.1")).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /AI setup|Set up the AI/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Inbox analysis" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Analyze inbox again" })).not.toBeInTheDocument();
  });
});
