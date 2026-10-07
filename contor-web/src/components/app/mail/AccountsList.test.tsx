import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { AccountsList } from "./AccountsList";
import { mockApi } from "@/test/api-mock";
import { renderWithProviders } from "@/test/render";

describe("AccountsList", () => {
  it("renders empty state when no accounts connected", async () => {
    mockApi({
      "GET /api/mail/accounts": { body: [] },
    });

    renderWithProviders(<AccountsList />);

    await waitFor(() => {
      expect(screen.getByText("No accounts connected")).toBeInTheDocument();
    });
    expect(screen.getByRole("button", { name: /connect outlook/i })).toBeInTheDocument();
  });

  it("renders connected accounts and handles sync", async () => {
    const user = userEvent.setup();
    const calls = mockApi({
      "GET /api/mail/accounts": {
        body: [
          {
            id: 1,
            provider: "MICROSOFT_GRAPH",
            emailAddress: "user@example.com",
            displayName: "Test User",
            status: "ACTIVE",
            lastSyncedAt: "2026-03-01T12:00:00Z",
          },
        ],
      },
      "POST /api/mail/accounts/1/sync": {
        body: { accountId: 1, status: "ACTIVE", messagesSynced: 3 },
      },
    });

    renderWithProviders(<AccountsList />);

    await waitFor(() => {
      expect(screen.getByText("user@example.com")).toBeInTheDocument();
      expect(screen.getByText("Test User")).toBeInTheDocument();
    });

    const syncBtn = screen.getByRole("button", { name: /sync now/i });
    await user.click(syncBtn);

    await waitFor(() => {
      expect(calls.some((c) => c.method === "POST" && c.path === "/api/mail/accounts/1/sync")).toBe(true);
    });
  });

  it("handles connect outlook button click", async () => {
    const user = userEvent.setup();
    const assignMock = vi.fn();
    Object.defineProperty(window, "location", {
      value: { assign: assignMock, href: "http://localhost:3000" },
      writable: true,
    });

    const calls = mockApi({
      "GET /api/mail/accounts": { body: [] },
      "POST /api/mail/accounts/connect": {
        body: { authorizeUrl: "https://login.microsoft.com/authorize" },
      },
    });

    renderWithProviders(<AccountsList />);

    const connectBtn = await screen.findByRole("button", { name: /connect outlook/i });
    await user.click(connectBtn);

    await waitFor(() => {
      expect(calls.some((c) => c.method === "POST" && c.path === "/api/mail/accounts/connect")).toBe(true);
      expect(assignMock).toHaveBeenCalledWith("https://login.microsoft.com/authorize");
    });
  });
});
