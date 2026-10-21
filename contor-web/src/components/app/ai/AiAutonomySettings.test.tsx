import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { mockApi } from "@/test/api-mock";
import { resetNavigation } from "@/test/next-navigation";
import { renderWithProviders } from "@/test/render";
import { AiAutonomySettings } from "./AiAutonomySettings";

vi.mock("next/navigation", () => import("@/test/next-navigation"));

const settings = {
  mailAccess: true,
  historyDays: 90,
  categories: "SUGGEST",
  records: "SUGGEST",
  calendar: "SUGGEST",
  tasks: "SUGGEST",
  replies: "SUGGEST",
  courtesyReplies: false,
  readHiddenMailboxes: true,
  ownerProfile: "",
  onboardedAt: "2026-10-01T10:00:00Z",
};

const latestRun = {
  id: 10,
  status: "COMPLETED",
  discoverySteps: 11,
  discoveryStepsDone: 11,
  totalEmails: 100,
  processedEmails: 100,
  failedEmails: 0,
  proposalsCreated: 7,
  autoApplied: 3,
  categoriesDiscovered: 2,
  createdAt: "2026-10-08T12:00:00Z",
};

function api(saved: Record<string, unknown> = settings) {
  return mockApi({
    "GET /api/ai/settings": { body: saved },
    "GET /api/ai/analysis/latest": { body: latestRun },
    "GET /api/proposals": { body: { items: [], totalElements: 0, totalPages: 0, page: 0, size: 20 } },
    "PUT /api/ai/settings": { body: saved },
    "GET /api/mail/accounts": {
      body: [{ id: 1, provider: "MICROSOFT_GRAPH", emailAddress: "work@example.com", status: "ACTIVE", aiEnabled: true, hidden: false }],
    },
  });
}

describe("AiAutonomySettings", () => {
  beforeEach(() => resetNavigation("/inbox"));

  it("starts with the mail-access checkbox, not framed in a box", async () => {
    api();
    renderWithProviders(<AiAutonomySettings />);

    const access = await screen.findByRole("checkbox", { name: /read and analyze my mailboxes/i });
    expect(access).toBeChecked();
    expect(access.closest("label")?.className).not.toMatch(/\bborder\b(?!-)|rounded/);
  });

  it("grays out the areas while mail access is off", async () => {
    api({ ...settings, mailAccess: false });
    renderWithProviders(<AiAutonomySettings />);

    await screen.findByRole("checkbox", { name: /read and analyze my mailboxes/i });
    expect(within(screen.getByRole("radiogroup", { name: "Calendar" })).getByRole("radio", { name: "Automatic" })).toBeDisabled();
    expect(screen.getByRole("combobox", { name: /how far back/i })).toBeDisabled();
  });

  it("saves a changed area", async () => {
    const calls = api();
    renderWithProviders(<AiAutonomySettings />);

    await userEvent.click(within(await screen.findByRole("radiogroup", { name: "Calendar" })).getByRole("radio", { name: "Automatic" }));
    await userEvent.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() => expect(calls.find((call) => call.method === "PUT")?.body).toMatchObject({ calendar: "AUTO", tasks: "SUGGEST" }));
  });

  it("is where the assistant is set up: the setup wizard, the mailboxes it reads, then the permissions", async () => {
    api();
    renderWithProviders(<AiAutonomySettings />);

    expect(await screen.findByRole("link", { name: "Open AI setup" })).toHaveAttribute("href", "/inbox?ai-setup=1");
    expect(await screen.findByRole("checkbox", { name: /work@example\.com/ })).toBeChecked();
    const headings = screen.getAllByRole("heading").map((heading) => heading.textContent);
    expect(headings).toEqual(["Setup", "Mailboxes", "Permissions", "Inbox analysis"]);
  });

  it("holds the inbox analysis (moved here from AI Models)", async () => {
    api();
    renderWithProviders(<AiAutonomySettings />);

    expect(await screen.findByRole("heading", { name: "Inbox analysis" })).toBeInTheDocument();
    expect(await screen.findByRole("button", { name: "Analyze inbox again" })).toBeEnabled();
  });
});
