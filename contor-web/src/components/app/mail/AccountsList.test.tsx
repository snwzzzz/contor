import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { AccountsList } from "./AccountsList";
import { mockApi } from "@/test/api-mock";
import { renderWithProviders } from "@/test/render";

vi.mock("next/navigation", () => import("@/test/next-navigation"));

const PROVIDERS = {
  "GET /api/mail/providers": {
    body: [
      { kind: "MICROSOFT_GRAPH", displayName: "Outlook", configured: true },
      { kind: "GMAIL", displayName: "Gmail", configured: true },
    ],
  },
};

describe("AccountsList", () => {
  it("renders empty state when no accounts connected", async () => {
    mockApi({
      ...PROVIDERS,
      "GET /api/mail/accounts": { body: [] },
    });

    renderWithProviders(<AccountsList />);

    await waitFor(() => {
      expect(screen.getByText("No accounts connected")).toBeInTheDocument();
    });
    expect(screen.getByRole("button", { name: /add account/i })).toBeInTheDocument();
  });

  it("offers each provider with its logo in the add account menu", async () => {
    const user = userEvent.setup();
    mockApi({ ...PROVIDERS, "GET /api/mail/accounts": { body: [] } });

    renderWithProviders(<AccountsList />);
    await user.click(await screen.findByRole("button", { name: /add account/i }));

    const outlook = await screen.findByRole("menuitem", { name: /outlook/i });
    const gmail = screen.getByRole("menuitem", { name: /gmail/i });
    expect(outlook.querySelector("svg[data-provider-logo=MICROSOFT_GRAPH]")).not.toBeNull();
    expect(gmail.querySelector("svg[data-provider-logo=GMAIL]")).not.toBeNull();
  });

  it("renders connected accounts and handles sync", async () => {
    const user = userEvent.setup();
    const calls = mockApi({
      ...PROVIDERS,
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

  it("connects Outlook from the add account menu", async () => {
    const user = userEvent.setup();
    const assignMock = vi.fn();
    Object.defineProperty(window, "location", {
      value: { assign: assignMock, href: "http://localhost:3000" },
      writable: true,
    });

    const calls = mockApi({
      ...PROVIDERS,
      "GET /api/mail/accounts": { body: [] },
      "POST /api/mail/accounts/connect": {
        body: { authorizeUrl: "https://login.microsoft.com/authorize" },
      },
    });

    renderWithProviders(<AccountsList />);

    await user.click(await screen.findByRole("button", { name: /add account/i }));
    await user.click(await screen.findByRole("menuitem", { name: /outlook/i }));

    await waitFor(() => {
      expect(calls.some((c) => c.method === "POST" && c.path === "/api/mail/accounts/connect")).toBe(true);
      expect(assignMock).toHaveBeenCalledWith("https://login.microsoft.com/authorize");
    });
    expect(calls.find((c) => c.path === "/api/mail/accounts/connect")?.body).toEqual({ provider: "MICROSOFT_GRAPH" });
  });

  it("offers Gmail and connects with the Gmail provider", async () => {
    const user = userEvent.setup();
    const assignMock = vi.fn();
    Object.defineProperty(window, "location", {
      value: { assign: assignMock, href: "http://localhost:3000" },
      writable: true,
    });
    const calls = mockApi({
      ...PROVIDERS,
      "GET /api/mail/accounts": { body: [] },
      "POST /api/mail/accounts/connect": { body: { authorizeUrl: "https://accounts.google.com/o/oauth2/v2/auth" } },
    });

    renderWithProviders(<AccountsList />);
    await user.click(await screen.findByRole("button", { name: /add account/i }));
    await user.click(await screen.findByRole("menuitem", { name: /gmail/i }));

    await waitFor(() => expect(assignMock).toHaveBeenCalledWith("https://accounts.google.com/o/oauth2/v2/auth"));
    expect(calls.find((c) => c.path === "/api/mail/accounts/connect")?.body).toEqual({ provider: "GMAIL" });
  });

  it("shows providers the server is not set up for as disabled", async () => {
    const user = userEvent.setup();
    const calls = mockApi({
      "GET /api/mail/providers": {
        body: [
          { kind: "MICROSOFT_GRAPH", displayName: "Outlook", configured: true },
          { kind: "GMAIL", displayName: "Gmail", configured: false },
        ],
      },
      "GET /api/mail/accounts": { body: [] },
    });

    renderWithProviders(<AccountsList />);
    await user.click(await screen.findByRole("button", { name: /add account/i }));

    const gmail = await screen.findByRole("menuitem", { name: /gmail/i });
    expect(gmail).toHaveAttribute("aria-disabled", "true");
    expect(gmail).toHaveTextContent(/not set up/i);
    expect(screen.getByRole("menuitem", { name: /outlook/i })).not.toHaveAttribute("aria-disabled");
    await user.click(gmail);
    expect(calls.some((c) => c.path === "/api/mail/accounts/connect")).toBe(false);
  });

  it("reconnects a Gmail mailbox through Google", async () => {
    const user = userEvent.setup();
    Object.defineProperty(window, "location", {
      value: { assign: vi.fn(), href: "http://localhost:3000" },
      writable: true,
    });
    const calls = mockApi({
      ...PROVIDERS,
      "GET /api/mail/accounts": {
        body: [{ id: 7, provider: "GMAIL", emailAddress: "me@gmail.com", status: "NEEDS_REAUTH" }],
      },
      "POST /api/mail/accounts/connect": { body: { authorizeUrl: "https://accounts.google.com/o/oauth2/v2/auth" } },
    });

    renderWithProviders(<AccountsList />);
    expect(await screen.findByText("Gmail")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /reconnect/i }));

    await waitFor(() =>
      expect(calls.find((c) => c.path === "/api/mail/accounts/connect")?.body).toEqual({
        provider: "GMAIL",
        emailHint: "me@gmail.com",
      }),
    );
  });

  it("offers a reconnect for a disconnected account that re-adds it with its address", async () => {
    const user = userEvent.setup();
    const assignMock = vi.fn();
    Object.defineProperty(window, "location", {
      value: { assign: assignMock, href: "http://localhost:3000" },
      writable: true,
    });
    const calls = mockApi({
      ...PROVIDERS,
      "GET /api/mail/accounts": {
        body: [{ id: 1, provider: "MICROSOFT_GRAPH", emailAddress: "me@outlook.com", status: "DISABLED" }],
      },
      "POST /api/mail/accounts/connect": { body: { authorizeUrl: "https://login.microsoft.com/authorize" } },
    });

    renderWithProviders(<AccountsList />);

    await user.click(await screen.findByRole("button", { name: /reconnect/i }));

    await waitFor(() => expect(assignMock).toHaveBeenCalledWith("https://login.microsoft.com/authorize"));
    expect(calls.find((c) => c.path === "/api/mail/accounts/connect")?.body).toEqual({
      provider: "MICROSOFT_GRAPH",
      emailHint: "me@outlook.com",
    });
    expect(screen.queryByRole("button", { name: /sync now/i })).not.toBeInTheDocument();
  });

  it("shows the demo mailbox as a demo, without sync or reconnect", async () => {
    mockApi({
      ...PROVIDERS,
      "GET /api/mail/accounts": {
        body: [{ id: 5, provider: "DEMO", emailAddress: "orders@nordhafen.example", displayName: "Nordhafen demo (orders)", status: "ACTIVE" }],
      },
    });

    renderWithProviders(<AccountsList />);

    expect(await screen.findByText("orders@nordhafen.example")).toBeInTheDocument();
    expect(screen.getByText("Demo")).toBeInTheDocument();
    expect(screen.getByText(/fictional demo mailbox/i)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /sync now/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /reconnect/i })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: /disconnect/i })).toBeInTheDocument();
  });

  it("offers a reconnect when the account needs re-authorization", async () => {
    mockApi({
      ...PROVIDERS,
      "GET /api/mail/accounts": {
        body: [{ id: 1, provider: "MICROSOFT_GRAPH", emailAddress: "me@outlook.com", status: "NEEDS_REAUTH" }],
      },
    });

    renderWithProviders(<AccountsList />);

    expect(await screen.findByRole("button", { name: /reconnect/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /disconnect/i })).toBeInTheDocument();
  });

  it("shows the result of the sign-in", async () => {
    mockApi({ ...PROVIDERS, "GET /api/mail/accounts": { body: [] } });

    renderWithProviders(<AccountsList mailError="Sign-in was cancelled." />);

    expect(await screen.findByRole("alert")).toHaveTextContent("Sign-in was cancelled.");
  });

  it("confirms a connected mailbox", async () => {
    mockApi({ ...PROVIDERS, "GET /api/mail/accounts": { body: [] } });

    renderWithProviders(<AccountsList connected />);

    expect(await screen.findByRole("status")).toHaveTextContent("Mailbox connected");
  });

  it("hides a mailbox's mail in contor with a toggle, and says the AI still reads it", async () => {
    const user = userEvent.setup();
    const account = { id: 4, provider: "MICROSOFT_GRAPH", emailAddress: "archive@example.com", status: "ACTIVE", aiEnabled: true, hidden: false };
    const calls = mockApi({
      ...PROVIDERS,
      "GET /api/mail/accounts": { body: [account] },
      "GET /api/ai/settings": { body: { mailAccess: true, historyDays: 90, categories: "SUGGEST", records: "SUGGEST", calendar: "SUGGEST",
        tasks: "SUGGEST", replies: "OFF", courtesyReplies: false, readHiddenMailboxes: true } },
      "PATCH /api/mail/accounts/4": { body: { ...account, hidden: true } },
    });

    renderWithProviders(<AccountsList />);
    const toggle = await screen.findByRole("checkbox", { name: /hide in contor/i });
    expect(toggle).not.toBeChecked();
    await user.click(toggle);

    await waitFor(() => expect(calls.find((c) => c.method === "PATCH")?.body).toEqual({ hidden: true }));
    expect(screen.getByRole("checkbox", { name: /hide in contor/i })).toBeChecked();
    expect(await screen.findByText(/the AI still reads it/i)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Settings › General" })).toHaveAttribute("href", expect.stringContaining("settings=general"));
  });

  it("shows the mailbox's profile picture", async () => {
    mockApi({
      ...PROVIDERS,
      "GET /api/mail/accounts": {
        body: [{ id: 3, provider: "MICROSOFT_GRAPH", emailAddress: "me@outlook.com", status: "ACTIVE", hasPhoto: true }],
      },
    });

    renderWithProviders(<AccountsList />);

    const title = await screen.findByText("me@outlook.com");
    expect(title.closest("[data-slot=card]")?.querySelector("img")).toHaveAttribute("src", expect.stringContaining("/api/mail/accounts/3/photo"));
  });
});
