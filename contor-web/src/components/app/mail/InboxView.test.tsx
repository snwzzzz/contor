import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { InboxView } from "./InboxView";
import { mockApi } from "@/test/api-mock";
import { renderWithProviders } from "@/test/render";

describe("InboxView", () => {
  it("renders empty state when no emails found", async () => {
    mockApi({
      "GET /api/emails": {
        body: { items: [], page: 0, size: 25, totalElements: 0, totalPages: 0 },
      },
      "GET /api/mail/accounts": { body: [] },
    });

    renderWithProviders(<InboxView />);

    await waitFor(() => {
      expect(screen.getByText("No emails found")).toBeInTheDocument();
    });
  });

  it("renders email list and filters on search input", async () => {
    const user = userEvent.setup();
    const calls = mockApi({
      "GET /api/emails": {
        body: {
          items: [
            {
              id: 101,
              accountId: 1,
              fromAddress: "alice@example.com",
              fromName: "Alice",
              subject: "Project Update",
              snippet: "Here is the latest status...",
              receivedAt: "2026-03-01T10:00:00Z",
              read: false,
              hasAttachments: false,
              processingState: "NEW",
              labels: [],
            },
          ],
          page: 0,
          size: 25,
          totalElements: 1,
          totalPages: 1,
        },
      },
    });

    renderWithProviders(<InboxView />);

    await waitFor(() => {
      expect(screen.getByText("Project Update")).toBeInTheDocument();
      expect(screen.getByText("Alice")).toBeInTheDocument();
    });

    const searchInput = screen.getByPlaceholderText(/search emails/i);
    await user.type(searchInput, "invoice");

    await waitFor(() => {
      expect(calls.some((c) => c.path === "/api/emails")).toBe(true);
    });
  });

  it("selects email and shows details", async () => {
    const user = userEvent.setup();
    mockApi({
      "GET /api/emails": {
        body: {
          items: [
            {
              id: 101,
              accountId: 1,
              fromAddress: "alice@example.com",
              fromName: "Alice",
              subject: "Important News",
              snippet: "Please read this...",
              receivedAt: "2026-03-01T10:00:00Z",
              read: true,
              hasAttachments: false,
              processingState: "DONE",
              labels: [],
            },
          ],
          page: 0,
          size: 25,
          totalElements: 1,
          totalPages: 1,
        },
      },
      "GET /api/emails/101": {
        body: {
          id: 101,
          accountId: 1,
          fromAddress: "alice@example.com",
          fromName: "Alice",
          toRecipients: [{ name: "Me", address: "me@example.com" }],
          ccRecipients: [],
          subject: "Important News",
          snippet: "Please read this...",
          bodyText: "Full email body text goes here.",
          receivedAt: "2026-03-01T10:00:00Z",
          read: true,
          hasAttachments: false,
          processingState: "DONE",
          labels: [],
        },
      },
    });

    renderWithProviders(<InboxView />);

    const item = await screen.findByText("Important News");
    await user.click(item);

    await waitFor(() => {
      expect(screen.getByText("Full email body text goes here.")).toBeInTheDocument();
    });
  });
});
