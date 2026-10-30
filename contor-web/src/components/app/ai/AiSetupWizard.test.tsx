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
  courtesyReplies: false,
  readHiddenMailboxes: true,
};

const mailboxes = [
  { id: 1, provider: "MICROSOFT_GRAPH", emailAddress: "work@example.com", status: "ACTIVE", aiEnabled: true, hidden: false },
  { id: 2, provider: "MICROSOFT_GRAPH", emailAddress: "home@example.com", status: "ACTIVE", aiEnabled: true, hidden: false },
];

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

function open(statusBody = readyStatus, accounts: unknown[] = mailboxes): RecordedCall[] {
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
    "GET /api/mail/accounts": { body: accounts },
    "PATCH /api/mail/accounts/2": { body: { ...mailboxes[1], aiEnabled: false } },
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

  it("walks through model, permissions, mailboxes and start", async () => {
    const calls = open();
    const dialog = await screen.findByRole("dialog", { name: "Set up your AI assistant" });
    expect(within(dialog).getByRole("list", { name: "Setup steps" })).toBeInTheDocument();
    expect(within(dialog).getAllByTestId("step-number").map((number) => number.textContent)).toEqual(["1", "2", "3", "4", "5", "6"]);
    expect(within(dialog).getAllByRole("listitem").map((step) => step.textContent)).toEqual([
      expect.stringContaining("Welcome"),
      expect.stringContaining("Model"),
      expect.stringContaining("Permissions"),
      expect.stringContaining("Mailboxes"),
      expect.stringContaining("Start"),
      expect.stringContaining("Analysis"),
    ]);
    // Room to breathe: generous padding and space between header, steps, content and buttons.
    expect(dialog).toHaveClass("gap-8", "sm:p-8");
    // Clipped, not just hidden: nothing (e.g. focusing a field) can scroll the header and steps out of view.
    expect(dialog).toHaveClass("overflow-clip");
    expect(dialog).not.toHaveClass("overflow-hidden");
    expect(within(dialog).getByText(/never leaves this computer/i)).toBeInTheDocument();

    await userEvent.click(within(dialog).getByRole("button", { name: "Get started" }));
    expect(await within(dialog).findByText("qwen3.5:9b")).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole("button", { name: "Next step" }));

    const next = within(dialog).getByRole("button", { name: "Next step" });
    expect(next).toBeDisabled();
    await userEvent.click(within(dialog).getByRole("checkbox", { name: /read and analyze my mailboxes/i }));
    await userEvent.click(within(within(dialog).getByRole("radiogroup", { name: "Calendar" })).getByRole("radio", { name: "Automatic" }));
    await userEvent.click(next);

    // Which mailboxes the AI reads: changed right away.
    await userEvent.click(await within(dialog).findByRole("checkbox", { name: /home@example\.com/ }));
    await waitFor(() => expect(calls.find((call) => call.method === "PATCH")?.body).toEqual({ aiEnabled: false }));
    await userEvent.click(within(dialog).getByRole("button", { name: "Next step" }));

    expect(within(dialog).getByText("work@example.com")).toBeInTheDocument();
    expect(within(dialog).queryByText("home@example.com")).not.toBeInTheDocument();
    expect(within(dialog).getByText("Calendar")).toBeInTheDocument();
    expect(within(dialog).getByText("Automatic")).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole("button", { name: "Start analysis" }));

    await waitFor(() => expect(calls.some((call) => call.method === "POST" && call.path === "/api/ai/analysis")).toBe(true));
    const saved = calls.find((call) => call.method === "PUT" && call.path === "/api/ai/settings");
    // The hidden-mailbox setting belongs to General and is not sent with the permissions.
    expect(saved?.body).toEqual({ ...settings, readHiddenMailboxes: undefined, mailAccess: true, calendar: "AUTO", completeOnboarding: true });
    expect(await within(dialog).findByText(/looking for categories/i)).toBeInTheDocument();
  });

  it("goes back by clicking a finished step; buttons carry arrows", async () => {
    open();
    const dialog = await screen.findByRole("dialog", { name: "Set up your AI assistant" });
    const steps = within(dialog).getByRole("list", { name: "Setup steps" });
    // Nothing is finished yet: no step to click.
    expect(within(steps).queryByRole("button")).not.toBeInTheDocument();

    const start = within(dialog).getByRole("button", { name: "Get started" });
    expect(start.querySelector("svg.lucide-arrow-right")).not.toBeNull();
    await userEvent.click(start);
    await within(dialog).findByText("qwen3.5:9b");
    await userEvent.click(within(dialog).getByRole("button", { name: "Next step" }));
    expect(within(dialog).getByRole("button", { name: "Back" }).querySelector("svg.lucide-arrow-left")).not.toBeNull();

    // Only finished steps are buttons; the current and upcoming ones are not.
    expect(within(steps).getAllByRole("button").map((button) => button.textContent)).toEqual([
      expect.stringContaining("Welcome"),
      expect.stringContaining("Model"),
    ]);
    await userEvent.click(within(steps).getByRole("button", { name: /Welcome/ }));

    expect(within(dialog).getByText(/never leaves this computer/i)).toBeInTheDocument();
    expect(within(steps).getAllByRole("listitem")[0]).toHaveAttribute("aria-current", "step");
  });

  it("cannot continue past the mailbox step until the AI may read at least one mailbox", async () => {
    open(readyStatus, [{ ...mailboxes[0], aiEnabled: false }]);
    const dialog = await screen.findByRole("dialog");
    await userEvent.click(within(dialog).getByRole("button", { name: "Get started" }));
    await within(dialog).findByText("qwen3.5:9b");
    await userEvent.click(within(dialog).getByRole("button", { name: "Next step" }));
    await userEvent.click(within(dialog).getByRole("checkbox", { name: /read and analyze my mailboxes/i }));
    await userEvent.click(within(dialog).getByRole("button", { name: "Next step" }));

    await within(dialog).findByRole("checkbox", { name: /work@example\.com/ });
    expect(within(dialog).getByRole("button", { name: "Next step" })).toBeDisabled();
  });

  it("cannot continue past the model step until the models are installed", async () => {
    open({ ...readyStatus, ready: false, models: [{ ...readyStatus.models[0], installed: false }] });
    const dialog = await screen.findByRole("dialog");

    await userEvent.click(within(dialog).getByRole("button", { name: "Get started" }));
    await within(dialog).findByText("Not installed");

    expect(within(dialog).getByRole("button", { name: "Next step" })).toBeDisabled();
  });

  it("closes back to the page underneath", async () => {
    open();
    await screen.findByRole("dialog");

    await userEvent.click(screen.getByRole("button", { name: "Close" }));

    expect(router.replace).toHaveBeenCalledWith("/inbox", { scroll: false });
  });
});
