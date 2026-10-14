import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import type { EmailThreadDto } from "@/lib/api/generated/model/emailThreadDto";
import { mockApi } from "@/test/api-mock";
import { renderWithProviders } from "@/test/render";
import { ThreadReplies } from "./ThreadReplies";

const settings = { mailAccess: true, historyDays: 90, categories: "SUGGEST", records: "SUGGEST", calendar: "SUGGEST", tasks: "SUGGEST", replies: "SUGGEST", courtesyReplies: false };

const message = {
  id: 8, accountId: 1, folder: "inbox", fromAddress: "anna@acme.com", fromName: "Anna Berger", subject: "Workshop",
  receivedAt: "2026-10-06T08:00:00Z", read: true, hasAttachments: false, processingState: "DONE" as const, labels: [],
  toRecipients: [], ccRecipients: [],
};

function thread(extra: Partial<EmailThreadDto> = {}): EmailThreadDto {
  return { messages: [message], replyDrafts: [], replySuggestion: "IDLE", ...extra };
}

const kept = {
  id: 4, emailId: 8, accountId: 1, toRecipients: [{ name: "Anna Berger", address: "anna@acme.com" }], ccRecipients: [],
  subject: "Re: Workshop", body: "Hallo Anna,\nich komme am Freitag.\nMax", status: "READY" as const, createdAt: "2026-10-07T08:00:00Z",
};

describe("ThreadReplies", () => {
  it("shows the assistant's pending suggestion with edit & keep and dismiss", async () => {
    mockApi({ "GET /api/ai/settings": { body: settings } });
    const pending = {
      id: 21, kind: "DRAFT_REPLY" as const, status: "PENDING" as const, createdAt: "2026-10-07T08:00:00Z",
      importance: "NORMAL" as const, importanceScore: 55,
      payload: { emailId: 8, to: [{ address: "anna@acme.com" }], cc: [], subject: "Re: Workshop", body: "Am [Tag] gerne." },
    };

    renderWithProviders(<ThreadReplies emailId={8} thread={thread({ pendingReplyProposal: pending })} />);

    const card = screen.getByRole("article", { name: "Re: Workshop" });
    expect(within(card).getByText("The assistant suggests a reply")).toBeInTheDocument();
    expect(within(card).getByRole("button", { name: "Edit & keep Re: Workshop" })).toBeInTheDocument();
    expect(within(card).getByRole("button", { name: "Dismiss Re: Workshop" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Suggest a reply/ })).not.toBeInTheDocument();
  });

  it("shows a kept draft as not sent, with copy, open in mail app and discard", async () => {
    const calls = mockApi({
      "GET /api/ai/settings": { body: settings },
      "POST /api/reply-drafts/4/discard": { body: { ...kept, status: "DISCARDED" } },
    });
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, "clipboard", { value: { writeText }, configurable: true });

    renderWithProviders(<ThreadReplies emailId={8} thread={thread({ replyDrafts: [kept] })} />);

    const draft = screen.getByRole("article", { name: "Draft — not sent" });
    expect(within(draft).getByRole("link", { name: /Open in mail app/ })).toHaveAttribute("href", expect.stringMatching(/^mailto:anna@acme\.com\?subject=Re%3A%20Workshop&body=/));
    await userEvent.click(within(draft).getByRole("button", { name: "Copy" }));
    expect(writeText).toHaveBeenCalledWith(expect.stringContaining("Subject: Re: Workshop"));
    expect(within(draft).getByRole("button", { name: "Copied" })).toBeInTheDocument();

    await userEvent.click(within(draft).getByRole("button", { name: "Discard" }));
    await waitFor(() => expect(calls.some((call) => call.method === "POST" && call.path === "/api/reply-drafts/4/discard")).toBe(true));
  });

  it("offers only copying for a draft too long for a mailto link", () => {
    mockApi({ "GET /api/ai/settings": { body: settings } });

    renderWithProviders(<ThreadReplies emailId={8} thread={thread({ replyDrafts: [{ ...kept, body: "x".repeat(3000) }] })} />);

    expect(screen.queryByRole("link", { name: /Open in mail app/ })).not.toBeInTheDocument();
    expect(screen.getByText(/Too long to open in the mail app/)).toBeInTheDocument();
  });

  it("asks for a reply on demand when reply drafts are on", async () => {
    const calls = mockApi({ "GET /api/ai/settings": { body: settings }, "POST /api/emails/8/reply-suggestion": { status: 202 } });

    renderWithProviders(<ThreadReplies emailId={8} thread={thread()} />);

    await userEvent.click(await screen.findByRole("button", { name: /Suggest a reply/ }));
    await waitFor(() => expect(calls.some((call) => call.method === "POST" && call.path === "/api/emails/8/reply-suggestion")).toBe(true));
  });

  it("says when the assistant is writing or failed", async () => {
    mockApi({ "GET /api/ai/settings": { body: settings } });

    const { rerender } = renderWithProviders(<ThreadReplies emailId={8} thread={thread({ replySuggestion: "RUNNING" })} />);
    expect(await screen.findByText("The assistant is writing a reply…")).toBeInTheDocument();

    rerender(<ThreadReplies emailId={8} thread={thread({ replySuggestion: "FAILED" })} />);
    expect(screen.getByText(/could not write a reply/)).toBeInTheDocument();
  });

  it("offers nothing when reply drafts are off", async () => {
    mockApi({ "GET /api/ai/settings": { body: { ...settings, replies: "OFF" } } });

    const { container } = renderWithProviders(<ThreadReplies emailId={8} thread={thread()} />);

    await waitFor(() => expect(container).toBeEmptyDOMElement());
  });
});
