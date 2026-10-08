import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { InboxView } from "./InboxView";
import { mockApi } from "@/test/api-mock";
import { renderWithProviders } from "@/test/render";

vi.mock("next/navigation", () => import("@/test/next-navigation"));

const ACCOUNTS = [
  { id: 1, provider: "MICROSOFT_GRAPH", emailAddress: "work@outlook.com", status: "ACTIVE", hasPhoto: false },
  { id: 2, provider: "MICROSOFT_GRAPH", emailAddress: "home@outlook.com", status: "ACTIVE", hasPhoto: true },
];

describe("InboxView", () => {
  it("renders empty state when no emails found", async () => {
    mockApi({
      "GET /api/emails": { body: page([], 0) },
      "GET /api/mail/accounts": { body: [] },
    });

    renderWithProviders(<InboxView />);

    expect(await screen.findByText("No emails found")).toBeInTheDocument();
  });

  it("renders email list and searches on input", async () => {
    const user = userEvent.setup();
    const requested: string[] = [];
    mockApi({
      "GET /api/mail/accounts": { body: [] },
      "GET /api/emails": ({ url }) => {
        requested.push(url.search);
        return { body: page([email(101, 1, "Project Update")], 1) };
      },
    });

    renderWithProviders(<InboxView />);

    expect(await screen.findByText("Project Update")).toBeInTheDocument();
    expect(screen.getByText("Alice")).toBeInTheDocument();

    await user.type(screen.getByPlaceholderText(/search emails/i), "invoice");

    await waitFor(() => expect(requested.some((search) => search.includes("q=invoice"))).toBe(true));
  });

  it("selects email and shows details", async () => {
    const user = userEvent.setup();
    mockApi({
      "GET /api/mail/accounts": { body: [] },
      "GET /api/emails": { body: page([email(101, 1, "Important News")], 1) },
      "GET /api/emails/101": {
        body: {
          ...email(101, 1, "Important News"),
          toRecipients: [{ name: "Me", address: "me@example.com" }],
          ccRecipients: [],
          bodyText: "Full email body text goes here.",
        },
      },
    });

    renderWithProviders(<InboxView />);

    await user.click(await screen.findByText("Important News"));

    expect(await screen.findByText("Full email body text goes here.")).toBeInTheDocument();
  });

  it("keeps the email list and the details in separate panes", async () => {
    mockApi({
      "GET /api/mail/accounts": { body: [] },
      "GET /api/emails": { body: page([email(101, 1, "Pane mail")], 1) },
    });

    renderWithProviders(<InboxView />);

    const list = await screen.findByRole("region", { name: "Email list" });
    expect(await within(list).findByText("Pane mail")).toBeInTheDocument();
    expect(screen.getByRole("region", { name: "Email details" })).toHaveTextContent("Select an email");
  });

  it("filters the unified inbox by account and shows the account on each email", async () => {
    const user = userEvent.setup();
    const requested: string[] = [];
    mockApi({
      "GET /api/mail/accounts": { body: ACCOUNTS },
      "GET /api/emails": ({ url }) => {
        requested.push(url.search);
        return { body: page([email(101, 1, "From work"), email(102, 2, "From home")], 1) };
      },
    });

    renderWithProviders(<InboxView />);

    const row = (await screen.findByText("From home")).closest("button");
    expect(row).toHaveTextContent("home@outlook.com");
    expect(screen.getByRole("button", { name: "All accounts" })).toHaveAttribute("aria-pressed", "true");

    await user.click(screen.getByRole("button", { name: "home@outlook.com" }));

    await waitFor(() => expect(requested.some((search) => search.includes("accountId=2"))).toBe(true));
    expect(screen.getByRole("button", { name: "home@outlook.com" })).toHaveAttribute("aria-pressed", "true");
  });

  it("shows the profile picture of accounts that have one", async () => {
    mockApi({
      "GET /api/mail/accounts": { body: ACCOUNTS },
      "GET /api/emails": { body: page([], 0) },
    });

    renderWithProviders(<InboxView />);

    const filter = await screen.findByRole("group", { name: "Filter by account" });
    const home = within(filter).getByRole("button", { name: "home@outlook.com" });
    expect(home.querySelector("img")).toHaveAttribute("src", expect.stringContaining("/api/mail/accounts/2/photo"));
    expect(within(filter).getByRole("button", { name: "work@outlook.com" }).querySelector("img")).toBeNull();
  });

  it("shows sender pictures: contact photo first, then the company logo, else the initial", async () => {
    const requested: string[] = [];
    mockApi({
      "GET /api/mail/accounts": { body: [] },
      "GET /api/emails": {
        body: page(
          [
            email(101, 1, "From a contact"),
            { ...email(102, 1, "From a company"), fromAddress: "talent@Siemens.com", fromName: "Siemens" },
            { ...email(103, 1, "From nowhere"), fromAddress: "x@nologo.example", fromName: "Nobody" },
          ],
          1,
        ),
      },
      "GET /api/senders/contact-photos": { body: [{ id: 9, emailAddress: "alice@example.com" }] },
      "GET /api/senders/logos": ({ url }) => {
        requested.push(url.search);
        return { body: ["siemens.com"] };
      },
    });

    renderWithProviders(<InboxView />);

    const contactRow = (await screen.findByText("From a contact")).closest("button");
    const companyRow = screen.getByText("From a company").closest("button");
    const plainRow = screen.getByText("From nowhere").closest("button");
    await waitFor(() =>
      expect(contactRow?.querySelector("img")).toHaveAttribute("src", expect.stringContaining("/api/senders/contact-photos/9")),
    );
    await waitFor(() =>
      expect(companyRow?.querySelector("img")).toHaveAttribute("src", expect.stringContaining("/api/senders/logos/siemens.com")),
    );
    // In the card's flex row the avatar stays a square at the top instead of stretching to the card height.
    expect(contactRow?.querySelector("img")).toHaveClass("self-start");
    expect(plainRow?.querySelector("img")).toBeNull();
    expect(plainRow).toHaveTextContent("N");
    expect(requested[0]).toContain("domain=siemens.com");
    expect(requested[0]).toContain("domain=nologo.example");
  });

  it("pages through all emails", async () => {
    const user = userEvent.setup();
    const requested: string[] = [];
    mockApi({
      "GET /api/mail/accounts": { body: [] },
      "GET /api/emails": ({ url }) => {
        requested.push(url.search);
        return { body: page([email(101, 1, "First page mail")], 3) };
      },
    });

    renderWithProviders(<InboxView />);

    await screen.findByText("First page mail");
    expect(screen.getByText(/page 1 of 3/i)).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /next/i }));

    await waitFor(() => expect(requested.some((search) => search.includes("page=1"))).toBe(true));
  });
});

function email(id: number, accountId: number, subject: string) {
  return {
    id,
    accountId,
    fromAddress: "alice@example.com",
    fromName: "Alice",
    subject,
    snippet: "Here is the latest status...",
    receivedAt: "2026-03-01T10:00:00Z",
    read: true,
    hasAttachments: false,
    processingState: "NEW",
    labels: [],
  };
}

function page(items: unknown[], totalPages: number) {
  return { items, page: 0, size: 50, totalElements: items.length * totalPages, totalPages };
}
