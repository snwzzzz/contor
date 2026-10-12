import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { apiError, mockApi, type RecordedCall } from "@/test/api-mock";
import { navigationState, resetNavigation, router } from "@/test/next-navigation";
import { renderWithProviders } from "@/test/render";
import { AiSetupWizard } from "./AiSetupWizard";

vi.mock("next/navigation", () => import("@/test/next-navigation"));

const settings = {
  mailAccess: false,
  historyDays: 90,
  categories: "SUGGEST",
  records: "SUGGEST",
  calendar: "SUGGEST",
  tasks: "SUGGEST",
  replies: "OFF",
};

const readyStatus = {
  reachable: true,
  version: "0.40.1",
  ready: true,
  models: [{ name: "qwen3.5:9b", roles: ["EXTRACTION", "ROUTING"], installed: true, pullState: "IDLE" }],
};

function run(status: string) {
  return {
    id: 10,
    status,
    totalEmails: 0,
    processedEmails: 0,
    failedEmails: 0,
    proposalsCreated: 0,
    autoApplied: 0,
    categoriesDiscovered: 0,
    createdAt: "2026-10-08T12:00:00Z",
  };
}

function open(statusBody = readyStatus): RecordedCall[] {
  let started = false;
  navigationState.search = "ai-setup=1";
  const calls = mockApi({
    "GET /api/ai/settings": { body: settings },
    "GET /api/ai/status": { body: statusBody },
    "GET /api/ai/analysis/latest": () => (started ? { body: run("DISCOVERING") } : apiError(404, "No inbox analysis has run yet")),
    "PUT /api/ai/settings": ({ init }) => ({ body: { ...settings, ...JSON.parse(String(init.body)), onboardedAt: "2026-10-08T12:00:00Z" } }),
    "POST /api/ai/analysis": () => {
      started = true;
      return { status: 201, body: run("QUEUED") };
    },
    "GET /api/proposals": { body: { items: [], page: 0, size: 50, totalElements: 0, totalPages: 0 } },
  });
  renderWithProviders(<AiSetupWizard />);
  return calls;
}

describe("AiSetupWizard", () => {
  beforeEach(() => resetNavigation("/inbox"));

  it("stays closed without the setup parameter", () => {
    mockApi({});
    renderWithProviders(<AiSetupWizard />);

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("walks through model, permissions and start", async () => {
    const calls = open();
    const dialog = await screen.findByRole("dialog", { name: "Set up your AI assistant" });
    expect(within(dialog).getByRole("list", { name: "Setup steps" })).toBeInTheDocument();
    expect(within(dialog).getByText(/never leaves this computer/i)).toBeInTheDocument();

    await userEvent.click(within(dialog).getByRole("button", { name: "Get started" }));
    expect(await within(dialog).findByText("qwen3.5:9b")).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole("button", { name: "Next" }));

    const next = within(dialog).getByRole("button", { name: "Next" });
    expect(next).toBeDisabled();
    await userEvent.click(within(dialog).getByRole("checkbox", { name: /read and analyze my mailboxes/i }));
    await userEvent.click(within(within(dialog).getByRole("radiogroup", { name: "Calendar" })).getByRole("radio", { name: "Automatic" }));
    await userEvent.click(next);

    expect(within(dialog).getByText("Calendar")).toBeInTheDocument();
    expect(within(dialog).getByText("Automatic")).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole("button", { name: "Start analysis" }));

    await waitFor(() => expect(calls.some((call) => call.method === "POST" && call.path === "/api/ai/analysis")).toBe(true));
    const saved = calls.find((call) => call.method === "PUT" && call.path === "/api/ai/settings");
    expect(saved?.body).toEqual({ ...settings, mailAccess: true, calendar: "AUTO", completeOnboarding: true });
    expect(await within(dialog).findByText(/looking for categories/i)).toBeInTheDocument();
  });

  it("cannot continue past the model step until the models are installed", async () => {
    open({ ...readyStatus, ready: false, models: [{ ...readyStatus.models[0], installed: false }] });
    const dialog = await screen.findByRole("dialog");

    await userEvent.click(within(dialog).getByRole("button", { name: "Get started" }));
    await within(dialog).findByText("Not installed");

    expect(within(dialog).getByRole("button", { name: "Next" })).toBeDisabled();
  });

  it("closes back to the page underneath", async () => {
    open();
    await screen.findByRole("dialog");

    await userEvent.click(screen.getByRole("button", { name: "Close" }));

    expect(router.replace).toHaveBeenCalledWith("/inbox", { scroll: false });
  });
});
