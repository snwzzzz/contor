import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { mockApi } from "@/test/api-mock";
import { resetNavigation } from "@/test/next-navigation";
import { renderWithProviders } from "@/test/render";
import { MailboxAiPicker } from "./MailboxAiPicker";

vi.mock("next/navigation", () => import("@/test/next-navigation"));

const work = { id: 1, provider: "MICROSOFT_GRAPH", emailAddress: "work@example.com", status: "ACTIVE", aiEnabled: true, hidden: false };
const home = { id: 2, provider: "MICROSOFT_GRAPH", emailAddress: "home@example.com", status: "ACTIVE", aiEnabled: false, hidden: false };
const archive = { id: 3, provider: "MICROSOFT_GRAPH", emailAddress: "archive@example.com", status: "ACTIVE", aiEnabled: true, hidden: true };

function settings(readHiddenMailboxes = true) {
  return {
    mailAccess: true, historyDays: 90, categories: "SUGGEST", records: "SUGGEST", calendar: "SUGGEST", tasks: "SUGGEST",
    replies: "OFF", courtesyReplies: false, readHiddenMailboxes,
  };
}

describe("MailboxAiPicker", () => {
  beforeEach(() => resetNavigation("/inbox"));

  it("lists every mailbox with whether the AI reads it", async () => {
    mockApi({ "GET /api/mail/accounts": { body: [work, home] }, "GET /api/ai/settings": { body: settings() } });
    renderWithProviders(<MailboxAiPicker />);

    expect(await screen.findByRole("checkbox", { name: /work@example\.com/ })).toBeChecked();
    expect(screen.getByRole("checkbox", { name: /home@example\.com/ })).not.toBeChecked();
  });

  it("turns a mailbox off for the AI right away", async () => {
    const calls = mockApi({
      "GET /api/mail/accounts": { body: [work, home] },
      "GET /api/ai/settings": { body: settings() },
      "PATCH /api/mail/accounts/1": { body: { ...work, aiEnabled: false } },
    });
    renderWithProviders(<MailboxAiPicker />);

    await userEvent.click(await screen.findByRole("checkbox", { name: /work@example\.com/ }));

    await waitFor(() => expect(calls.find((call) => call.method === "PATCH")?.body).toEqual({ aiEnabled: false }));
    expect(screen.getByRole("checkbox", { name: /work@example\.com/ })).not.toBeChecked();
  });

  it("says when a mailbox is hidden in contor and whether the AI still reads it", async () => {
    mockApi({ "GET /api/mail/accounts": { body: [archive] }, "GET /api/ai/settings": { body: settings(true) } });
    const { unmount } = renderWithProviders(<MailboxAiPicker />);
    expect(await screen.findByText(/Hidden in contor; the AI still reads it/)).toBeInTheDocument();
    unmount();

    mockApi({ "GET /api/mail/accounts": { body: [archive] }, "GET /api/ai/settings": { body: settings(false) } });
    renderWithProviders(<MailboxAiPicker />);
    expect(await screen.findByText(/Hidden in contor; the AI skips hidden mailboxes/)).toBeInTheDocument();
    expect(screen.getByRole("checkbox", { name: /archive@example\.com/ })).toBeDisabled();
  });

  it("leaves out disconnected mailboxes and points to Accounts when there is none", async () => {
    mockApi({
      "GET /api/mail/accounts": { body: [{ ...work, status: "DISABLED" }] },
      "GET /api/ai/settings": { body: settings() },
    });
    renderWithProviders(<MailboxAiPicker />);

    expect(await screen.findByText("No mailbox connected yet.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Connect a mailbox" })).toHaveAttribute("href", "/inbox?settings=accounts");
    expect(screen.queryByRole("checkbox")).not.toBeInTheDocument();
  });
});
