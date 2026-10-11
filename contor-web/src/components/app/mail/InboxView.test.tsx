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
  it("renders empty state when no emails found, keeping the search to change it", async () => {
    mockApi({
      "GET /api/emails": { body: page([], 0) },
      "GET /api/mail/accounts": { body: [] },
    });

    renderWithProviders(<InboxView />);

    expect(await screen.findByText("No emails found")).toBeInTheDocument();
    expect(screen.getByRole("combobox", { name: "Search emails" })).toBeInTheDocument();
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

    await user.type(screen.getByRole("combobox", { name: "Search emails" }), "invoice");

    await waitFor(() => expect(requested.some((search) => search.includes("q=invoice"))).toBe(true));
  });

  it("searches only in the chosen field and picks a sender from the suggestions", async () => {
    const user = userEvent.setup();
    const requested: string[] = [];
    mockApi({
      "GET /api/mail/accounts": { body: [] },
      "GET /api/emails": ({ url }) => {
        requested.push(url.search);
        return { body: page([email(101, 1, "Project Update")], 1) };
      },
      "GET /api/emails/suggestions": { body: [{ kind: "COMPANY", label: "acme.com", value: "@acme.com", emailCount: 3 }] },
    });

    renderWithProviders(<InboxView />);
    await screen.findByText("Project Update");

    await user.click(screen.getByRole("button", { name: "Search in: All" }));
    await user.click(await screen.findByRole("menuitemradio", { name: "Subject" }));
    await user.type(screen.getByRole("combobox", { name: "Search emails" }), "acm");
    await waitFor(() => expect(requested.some((search) => search.includes("q=acm") && search.includes("field=SUBJECT"))).toBe(true));

    await user.click(await screen.findByRole("option", { name: /acme\.com/ }));

    await waitFor(() => expect(requested.some((search) => search.includes("q=%40acme.com") && search.includes("field=PEOPLE"))).toBe(true));
  });

  it("lists the emails under date headings like Outlook and marks the unread ones", async () => {
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date(2026, 2, 5, 12, 0));
    try {
      mockApi({
        "GET /api/mail/accounts": { body: [] },
        "GET /api/emails": {
          body: page([
            { ...email(101, 1, "Fresh news"), receivedAt: new Date(2026, 2, 5, 9, 15).toISOString(), read: false },
            { ...email(102, 1, "Old news"), receivedAt: new Date(2026, 1, 2, 9, 15).toISOString() },
          ], 1),
        },
      });

      renderWithProviders(<InboxView />);

      const list = await screen.findByRole("region", { name: "Email list" });
      expect(await within(list).findByRole("heading", { name: "Today" })).toBeInTheDocument();
      expect(within(list).getByRole("heading", { name: "February 2026" })).toBeInTheDocument();
      const fresh = within(list).getByText("Fresh news").closest("button");
      expect(fresh).toHaveAttribute("data-unread", "true");
      expect(fresh).toHaveTextContent("09:15");
      expect(within(list).getByText("Old news").closest("button")).not.toHaveAttribute("data-unread");
    } finally {
      vi.useRealTimers();
    }
  });

  it("marks threads and answered emails and filters the ones not answered yet", async () => {
    const user = userEvent.setup();
    const requested: string[] = [];
    mockApi({
      "GET /api/mail/accounts": { body: [] },
      "GET /api/emails": ({ url }) => {
        requested.push(url.search);
        return { body: page([{ ...email(101, 1, "Quarterly plan"), threadSize: 3, answered: true }, email(102, 1, "Lunch?")], 1) };
      },
    });

    renderWithProviders(<InboxView />);

    const answered = (await screen.findByText("Quarterly plan")).closest("button");
    expect(within(answered as HTMLElement).getByLabelText("Answered")).toBeInTheDocument();
    expect(within(answered as HTMLElement).getByLabelText("3 messages")).toBeInTheDocument();
    const plain = screen.getByText("Lunch?").closest("button");
    expect(within(plain as HTMLElement).queryByLabelText("Answered")).not.toBeInTheDocument();
    expect(within(plain as HTMLElement).queryByLabelText(/messages/)).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /^Filter/ }));
    await user.click(await screen.findByRole("menuitemcheckbox", { name: "Not answered" }));

    await waitFor(() => expect(requested.some((search) => search.includes("answered=false"))).toBe(true));
    expect(screen.getByRole("button", { name: /^Filter/ })).toHaveTextContent("1");
  });

  it("shows the conversation in the reading pane, your replies marked as yours", async () => {
    const user = userEvent.setup();
    mockApi({
      "GET /api/mail/accounts": { body: [{ id: 1, provider: "MICROSOFT_GRAPH", emailAddress: "me@outlook.com", status: "ACTIVE", hasPhoto: false }] },
      "GET /api/emails": { body: page([{ ...email(102, 1, "Re: Offer"), threadSize: 2, answered: true }], 1) },
      "GET /api/emails/102/thread": {
        body: {
          conversationId: "c-9",
          messages: [
            { ...email(100, 1, "Offer"), bodyText: "Here is our offer.", toRecipients: [], ccRecipients: [] },
            { ...email(101, 1, "Re: Offer"), folder: "sent", fromName: "Me", fromAddress: "me@outlook.com", bodyText: "Sounds good, thanks.",
              toRecipients: [], ccRecipients: [], receivedAt: "2026-03-02T10:00:00Z" },
          ],
        },
      },
    });

    renderWithProviders(<InboxView />);
    await user.click(await screen.findByText("Re: Offer"));

    const thread = await screen.findByRole("region", { name: "Conversation" });
    // The subject box stays on top; only the history below it scrolls (messages vanish under the subject).
    const history = within(thread).getByRole("list", { name: "Messages" });
    expect(history).not.toContainElement(within(thread).getByRole("heading", { name: "Re: Offer" }));
    expect(history.className).toContain("overflow-y-auto");
    const messages = within(history).getAllByRole("article");
    expect(messages).toHaveLength(2);
    expect(within(messages[1]).getByText("You")).toBeInTheDocument();
    expect(within(messages[1]).getByText("Sounds good, thanks.")).toBeInTheDocument();
    // The earlier message is collapsed to its first line until opened.
    expect(within(messages[0]).queryByText("Here is our offer.", { selector: "div" })).not.toBeInTheDocument();
    await user.click(within(messages[0]).getByRole("button", { name: /Alice/ }));
    expect(within(messages[0]).getByText("Here is our offer.", { selector: "div" })).toBeInTheDocument();
  });

  it("selects email and shows details", async () => {
    const user = userEvent.setup();
    mockApi({
      "GET /api/mail/accounts": { body: [] },
      "GET /api/emails": { body: page([email(101, 1, "Important News")], 1) },
      "GET /api/emails/101/thread": {
        body: {
          conversationId: "c-1",
          messages: [{
            ...email(101, 1, "Important News"),
            toRecipients: [{ name: "Me", address: "me@example.com" }],
            ccRecipients: [],
            bodyText: "Full email body text goes here.",
          }],
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
    // Search and filter sit compactly on top of the list, not across the page.
    expect(within(list).getByRole("combobox", { name: "Search emails" })).toBeInTheDocument();
    expect(within(list).getByRole("button", { name: /^Filter/ })).toBeInTheDocument();
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
    await user.click(screen.getByRole("button", { name: /^Filter/ }));
    expect(await screen.findByRole("menuitemradio", { name: "All accounts" })).toHaveAttribute("aria-checked", "true");

    await user.click(screen.getByRole("menuitemradio", { name: "home@outlook.com" }));

    await waitFor(() => expect(requested.some((search) => search.includes("accountId=2"))).toBe(true));
    expect(screen.getByRole("button", { name: /^Filter/ })).toHaveTextContent("1");
    await user.click(screen.getByRole("button", { name: /^Filter/ }));
    expect(await screen.findByRole("menuitemradio", { name: "home@outlook.com" })).toHaveAttribute("aria-checked", "true");
  });

  it("shows the profile picture of accounts that have one", async () => {
    mockApi({
      "GET /api/mail/accounts": { body: ACCOUNTS },
      "GET /api/emails": { body: page([], 0) },
    });

    renderWithProviders(<InboxView />);

    await userEvent.click(await screen.findByRole("button", { name: /^Filter/ }));
    const home = await screen.findByRole("menuitemradio", { name: "home@outlook.com" });
    expect(home.querySelector("img")).toHaveAttribute("src", expect.stringContaining("/api/mail/accounts/2/photo"));
    expect(screen.getByRole("menuitemradio", { name: "work@outlook.com" }).querySelector("img")).toBeNull();
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

    const mail = await screen.findByText("First page mail");
    expect(screen.getByText(/page 1 of 3/i)).toBeInTheDocument();
    // The pager ends the scrolling list (after the last email), it is not pinned to the bottom of the page.
    const pager = screen.getByRole("navigation", { name: "Pages" });
    const scroller = screen.getByTestId("email-list-scroll");
    expect(scroller).toContainElement(pager);
    expect(mail.compareDocumentPosition(pager) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();

    scroller.scrollTop = 800;
    await user.click(screen.getByRole("button", { name: /next/i }));

    await waitFor(() => expect(requested.some((search) => search.includes("page=1"))).toBe(true));
    // The next page starts at its top, not where the pager was.
    expect(scroller.scrollTop).toBe(0);
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
