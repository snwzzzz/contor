import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { mockApi } from "@/test/api-mock";
import { renderWithProviders } from "@/test/render";
import { RecordDetail } from "./RecordDetail";

vi.mock("next/navigation", () => import("@/test/next-navigation"));

function field(id: number, slug: string, name: string, type: string) {
  return { id, slug, name, type, options: {}, required: false, uniqueValue: false, position: id };
}

const people = {
  id: 1,
  slug: "people",
  name: "People",
  system: true,
  matchKeys: ["email"],
  displayConfig: {},
  schemaVersion: 1,
  createdBy: "USER",
  fields: [field(1, "full_name", "Full name", "TEXT"), field(2, "email", "Email", "EMAIL"), field(3, "phone", "Phone", "PHONE"),
    field(4, "website", "Website", "URL"), field(5, "notes", "Notes", "LONG_TEXT")],
};

const anna = {
  id: 5,
  collectionId: 1,
  title: "Anna Berger",
  values: { full_name: "Anna Berger", email: "Anna.Berger@acme.com", phone: "+49 89 1234567", website: "https://acme.com", notes: null },
  extra: {},
  createdAt: "2026-10-01T00:00:00Z",
  createdBy: "AI",
};

function api(contactPhotos: unknown[], logos: string[]) {
  return mockApi({
    "GET /api/collections/1": { body: people },
    "GET /api/collections/1/records/5": { body: anna },
    "GET /api/senders/contact-photos": { body: contactPhotos },
    "GET /api/senders/logos": { body: logos },
  });
}

describe("RecordDetail", () => {
  it("shows the record as a profile without a banner: breadcrumbs, the picture next to title | collection, and every field", async () => {
    api([{ id: 9, emailAddress: "anna.berger@acme.com" }], ["acme.com"]);

    renderWithProviders(<RecordDetail collectionId={1} recordId={5} />);

    expect(await screen.findByRole("heading", { level: 1, name: "Anna Berger" })).toBeInTheDocument();
    expect(screen.queryByTestId("record-banner")).not.toBeInTheDocument();
    const picture = await screen.findByRole("img", { name: "Anna Berger" });
    expect(picture).toHaveAttribute("width", "64");
    const header = screen.getByRole("heading", { level: 1, name: "Anna Berger" }).closest("header") as HTMLElement;
    expect(within(header).getByText("People")).toBeInTheDocument();
    expect(within(header).getByText(/^Added by/)).toBeInTheDocument();
    expect(within(header).getByRole("link", { name: "Table view" })).toHaveAttribute("href", "/collections/1");
    expect(picture.getAttribute("src")).toContain("/api/senders/contact-photos/9");
    const trail = screen.getByRole("navigation", { name: "Breadcrumb" });
    expect(within(trail).getByRole("link", { name: "People" })).toHaveAttribute("href", "/collections/1");
    expect(within(trail).getByText("Anna Berger")).toHaveAttribute("aria-current", "page");
    expect(screen.getByRole("link", { name: "Close" })).toHaveAttribute("href", "/collections/1");

    // Every field is an input with its current value.
    const details = screen.getByRole("form", { name: "Details" });
    expect(within(details).getByRole("textbox", { name: "Title" })).toHaveValue("Anna Berger");
    expect(within(details).getByRole("textbox", { name: "Email" })).toHaveValue("Anna.Berger@acme.com");
    expect(within(details).getByRole("textbox", { name: "Phone" })).toHaveValue("+49 89 1234567");
    expect(within(details).getByRole("textbox", { name: "Notes" })).toHaveValue("");
    expect(within(details).getByRole("link", { name: "Send email to Anna.Berger@acme.com" })).toHaveAttribute("href", "mailto:Anna.Berger@acme.com");
    // Nothing to save until something changes.
    expect(within(details).queryByRole("button", { name: "Save" })).not.toBeInTheDocument();
  });

  it("has a sidebar version: the same summary and tabs, without breadcrumbs, page title or page commands", async () => {
    api([], []);

    renderWithProviders(<RecordDetail collectionId={1} recordId={5} variant="sheet" />);

    const details = await screen.findByRole("form", { name: "Details" });
    expect(within(details).getByRole("textbox", { name: "Email" })).toHaveValue("Anna.Berger@acme.com");
    expect(screen.getByRole("tablist", { name: "Record sections" })).toBeInTheDocument();
    expect(screen.getAllByRole("tab").map((tab) => tab.textContent)).toEqual(["Details", "Emails", "Reports", "Connections"]);
    // The sidebar's own header names the collection and the title.
    expect(screen.queryByText("People")).not.toBeInTheDocument();
    expect(screen.getByText(/^Added by the assistant/)).toBeInTheDocument();
    expect(screen.getByTestId("record-initial")).toHaveStyle({ width: "40px" });
    expect(screen.queryByRole("navigation", { name: "Breadcrumb" })).not.toBeInTheDocument();
    expect(screen.queryByRole("heading", { level: 1 })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Table view" })).not.toBeInTheDocument();
  });

  it("leads through the calendar for an event, not through the collection table", async () => {
    const events = { ...people, id: 3, slug: "events", name: "Calendar", matchKeys: [], fields: [field(1, "starts_at", "Starts", "DATETIME")] };
    mockApi({
      "GET /api/collections/3": { body: events },
      "GET /api/collections/3/records/12": { body: { ...anna, id: 12, collectionId: 3, title: "Leitungsrunde", values: {} } },
      "GET /api/senders/contact-photos": { body: [] },
    });

    renderWithProviders(<RecordDetail collectionId={3} recordId={12} />);

    const trail = await screen.findByRole("navigation", { name: "Breadcrumb" });
    expect(within(trail).getAllByRole("link").map((link) => [link.textContent, link.getAttribute("href")])).toEqual([
      ["Home", "/"],
      ["Calendar", "/calendar"],
    ]);
    expect(screen.getByRole("link", { name: "Close" })).toHaveAttribute("href", "/calendar");
  });

  it("saves only the changed fields of the details and can discard edits", async () => {
    const calls = mockApi({
      "GET /api/collections/1": { body: people },
      "GET /api/collections/1/records/5": { body: anna },
      "GET /api/senders/contact-photos": { body: [] },
      "GET /api/senders/logos": { body: [] },
      "PUT /api/collections/1/records/5": { body: { ...anna, values: { ...anna.values, phone: "+49 30 999" } } },
    });
    const user = userEvent.setup();
    renderWithProviders(<RecordDetail collectionId={1} recordId={5} />);
    const details = await screen.findByRole("form", { name: "Details" });

    await user.clear(within(details).getByRole("textbox", { name: "Notes" }));
    await user.type(within(details).getByRole("textbox", { name: "Notes" }), "Call back");
    await user.click(within(details).getByRole("button", { name: "Discard" }));
    expect(within(details).getByRole("textbox", { name: "Notes" })).toHaveValue("");

    const phone = within(details).getByRole("textbox", { name: "Phone" });
    await user.clear(phone);
    await user.type(phone, "+49 30 999");
    expect(within(details).getByText("1 change")).toBeInTheDocument();
    await user.click(within(details).getByRole("button", { name: "Save" }));

    await waitFor(() => expect(calls.find((call) => call.method === "PUT")?.body).toEqual({ values: { phone: "+49 30 999" } }));
    await waitFor(() => expect(within(details).queryByRole("button", { name: "Save" })).not.toBeInTheDocument());
  });

  it("shows why saving failed", async () => {
    mockApi({
      "GET /api/collections/1": { body: people },
      "GET /api/collections/1/records/5": { body: anna },
      "GET /api/senders/contact-photos": { body: [] },
      "GET /api/senders/logos": { body: [] },
      "PUT /api/collections/1/records/5": { status: 400, body: { message: "email: not a valid email address", status: 400 } },
    });
    const user = userEvent.setup();
    renderWithProviders(<RecordDetail collectionId={1} recordId={5} />);
    const details = await screen.findByRole("form", { name: "Details" });

    await user.type(within(details).getByRole("textbox", { name: "Email" }), "x");
    await user.click(within(details).getByRole("button", { name: "Save" }));

    expect(await within(details).findByRole("alert")).toHaveTextContent("email: not a valid email address");
  });

  it("falls back to the logo of the email domain", async () => {
    api([], ["acme.com"]);

    renderWithProviders(<RecordDetail collectionId={1} recordId={5} />);

    const picture = await screen.findByRole("img", { name: "Anna Berger" });
    expect(picture.getAttribute("src")).toContain("/api/senders/logos/acme.com");
  });

  it("never uses a free-mail provider's logo for a person", async () => {
    mockApi({
      "GET /api/collections/1": { body: people },
      "GET /api/collections/1/records/5": { body: { ...anna, values: { ...anna.values, email: "anna@gmail.com", website: null } } },
      "GET /api/senders/contact-photos": { body: [] },
      "GET /api/senders/logos": { body: ["gmail.com"] },
    });

    renderWithProviders(<RecordDetail collectionId={1} recordId={5} />);

    await screen.findByRole("heading", { level: 1, name: "Anna Berger" });
    expect(screen.queryByRole("img", { name: "Anna Berger" })).not.toBeInTheDocument();
    expect(screen.getByTestId("record-initial")).toHaveTextContent("A");
  });

  it("has a Reports tab with its count: the question with the input and the report list; the details tab only shows the fields", async () => {
    mockApi({
      "GET /api/collections/1": { body: people },
      "GET /api/collections/1/records/5": { body: anna },
      "GET /api/senders/contact-photos": { body: [] },
      "GET /api/senders/logos": { body: [] },
      "GET /api/ai/status": { body: { reachable: true, ready: true, models: [] } },
      "GET /api/collections/1/records/5/reports": {
        body: [{ id: 5, title: "Overview", state: "READY", claimsTotal: 1, claimsVerified: 1, followUpCount: 0, createdAt: "2026-10-14T10:00:00Z" }],
      },
    });
    renderWithProviders(<RecordDetail collectionId={1} recordId={5} />);

    const tabs = await screen.findByRole("tablist", { name: "Record sections" });
    const reportsTab = within(tabs).getByRole("tab", { name: /Reports/ });
    await waitFor(() => expect(reportsTab).toHaveTextContent("1"));
    expect(screen.queryByRole("textbox", { name: "Ask about this record" })).not.toBeInTheDocument();

    await userEvent.click(reportsTab);

    const panel = screen.getByRole("tabpanel", { name: /Reports/ });
    expect(within(panel).getByRole("heading", { name: "What do you want to know about Anna Berger?" })).toBeInTheDocument();
    // On the Reports tab the record page is exactly as high as the screen: a column whose panel takes the rest.
    expect(panel.closest("article")).toHaveClass("lg:flex", "lg:flex-1", "lg:min-h-0", "lg:flex-col");
    expect(panel).toHaveClass("lg:min-h-0", "lg:flex-1");
    const list = within(panel).getByRole("complementary", { name: "Report list" });
    expect(await within(list).findByRole("button", { name: /Overview/ })).toBeInTheDocument();
  });

  it("opens on the details tab and switches to the emails the record comes up in", async () => {
    mockApi({
      "GET /api/collections/1": { body: people },
      "GET /api/collections/1/records/5": { body: anna },
      "GET /api/senders/contact-photos": { body: [] },
      "GET /api/senders/logos": { body: [] },
      "GET /api/collections/1/records/5/emails": {
        body: {
          items: [{ id: 31, accountId: 1, fromAddress: "anna.berger@acme.com", fromName: "Anna Berger", subject: "Interview invitation",
            snippet: "We would like to invite you", receivedAt: "2026-10-06T08:00:00Z", read: true, hasAttachments: false,
            processingState: "DONE", labels: [] }],
          page: 0, size: 25, totalElements: 1, totalPages: 1,
        },
      },
      "GET /api/emails/31/thread": {
        body: { messages: [{ id: 31, accountId: 1, fromAddress: "anna.berger@acme.com", fromName: "Anna Berger", subject: "Interview invitation",
          receivedAt: "2026-10-06T08:00:00Z", read: true, hasAttachments: false, processingState: "DONE", labels: [],
          toRecipients: [], ccRecipients: [], bodyText: "We would like to invite you to an interview." }], replyDrafts: [], replySuggestion: "IDLE" },
      },
    });

    renderWithProviders(<RecordDetail collectionId={1} recordId={5} />);

    const tabs = await screen.findByRole("tablist", { name: "Record sections" });
    expect(within(tabs).getByRole("tab", { name: /Details/ })).toHaveAttribute("aria-selected", "true");
    const emailsTab = within(tabs).getByRole("tab", { name: /Emails/ });
    await waitFor(() => expect(emailsTab).toHaveTextContent("1"));

    await userEvent.click(emailsTab);
    const panel = screen.getByRole("tabpanel", { name: /Emails/ });
    await userEvent.click(await within(panel).findByRole("button", { name: /Interview invitation/ }));
    expect(await within(panel).findByText("We would like to invite you to an interview.")).toBeInTheDocument();
  });

  it("never scrolls the tab bar vertically: the active underline stays inside it", async () => {
    api([], []);
    renderWithProviders(<RecordDetail collectionId={1} recordId={5} />);

    const tabs = await screen.findByRole("tablist", { name: "Record sections" });
    expect(tabs).toHaveClass("overflow-y-hidden");
    for (const tab of within(tabs).getAllByRole("tab")) expect(tab).not.toHaveClass("-mb-px");
    // A click leaves no box around the tab; keyboard focus still shows a ring.
    for (const tab of within(tabs).getAllByRole("tab")) expect(tab).toHaveClass("outline-none", "focus-visible:ring-2");
  });

  it("keeps the emails in the page: the page scrolls, the reading pane stays in view and rows never clip", async () => {
    mockApi({
      "GET /api/collections/1": { body: people },
      "GET /api/collections/1/records/5": { body: anna },
      "GET /api/senders/contact-photos": { body: [] },
      "GET /api/senders/logos": { body: [] },
      "GET /api/collections/1/records/5/emails": {
        body: {
          items: [{ id: 31, accountId: 1, fromAddress: "anna.berger@acme.com", fromName: "Anna Berger",
            subject: "RE: STATUS CMR-BL-260718 / UNLOADING SLOT FOR THE TRUCK AT THE BORDER", snippet: "Dear sir",
            receivedAt: "2026-10-06T08:00:00Z", read: false, hasAttachments: false, processingState: "DONE", labels: [], threadSize: 3, answered: true }],
          page: 0, size: 50, totalElements: 1, totalPages: 1,
        },
      },
    });
    const { container } = renderWithProviders(<RecordDetail collectionId={1} recordId={5} />);

    await userEvent.click(await screen.findByRole("tab", { name: /Emails/ }));

    const panel = screen.getByRole("tabpanel", { name: /Emails/ });
    const row = await within(panel).findByRole("button", { name: /UNLOADING SLOT/ });
    // No fixed-height panes: the list grows with the page, which scrolls.
    expect(container.querySelector("article")).not.toHaveClass("lg:flex-1");
    expect(within(panel).getByTestId("email-list")).not.toHaveClass("overflow-y-auto");
    expect(within(panel).getByRole("region", { name: "Email details" })).toHaveClass("lg:sticky");
    // A long subject truncates instead of pushing the date and marks out of the row.
    expect(row.querySelector(".grid")).toHaveClass("grid-cols-[minmax(0,1fr)]");
    expect(within(panel).getByText("1 email")).toBeInTheDocument();

    // An open email takes only the height it needs (no empty pane), long conversations stop at the screen height.
    await userEvent.click(row);
    const pane = within(panel).getByRole("region", { name: "Email details" });
    expect(pane.className).toMatch(/lg:max-h-\[/);
    expect(pane.className).not.toMatch(/lg:h-\[/);
  });

  it("filters the record's emails", async () => {
    const mail = (id: number, subject: string, snippet: string) => ({ id, accountId: 1, fromAddress: "anna.berger@acme.com", fromName: "Anna Berger",
      subject, snippet, receivedAt: "2026-10-06T08:00:00Z", read: true, hasAttachments: false, processingState: "DONE", labels: [] });
    mockApi({
      "GET /api/collections/1": { body: people },
      "GET /api/collections/1/records/5": { body: anna },
      "GET /api/senders/contact-photos": { body: [] },
      "GET /api/senders/logos": { body: [] },
      "GET /api/collections/1/records/5/emails": {
        body: { items: [mail(31, "Interview invitation", "See you on Monday"), mail(32, "Invoice", "Please pay the interview fee")],
          page: 0, size: 25, totalElements: 2, totalPages: 1 },
      },
    });

    renderWithProviders(<RecordDetail collectionId={1} recordId={5} />);
    await userEvent.click(await screen.findByRole("tab", { name: /Emails/ }));
    const panel = screen.getByRole("tabpanel", { name: /Emails/ });
    await within(panel).findByRole("button", { name: /Invoice/ });

    await userEvent.type(within(panel).getByRole("combobox", { name: "Search emails" }), "interview");
    expect(within(panel).getByRole("button", { name: /Interview invitation/ })).toBeInTheDocument();
    expect(within(panel).getByRole("button", { name: /Invoice/ })).toBeInTheDocument();

    await userEvent.click(within(panel).getByRole("button", { name: "Search in: All" }));
    await userEvent.click(await screen.findByRole("menuitemradio", { name: "Subject" }));
    expect(within(panel).queryByRole("button", { name: /Invoice/ })).not.toBeInTheDocument();
    expect(within(panel).getByText("1 of 2 emails")).toBeInTheDocument();
  });

  it("shows the record's emails like the inbox: sender pictures in a list pane, the selected email in a details pane", async () => {
    mockApi({
      "GET /api/collections/1": { body: people },
      "GET /api/collections/1/records/5": { body: anna },
      "GET /api/senders/contact-photos": { body: [] },
      "GET /api/senders/logos": { body: ["acme.com"] },
      "GET /api/collections/1/records/5/emails": {
        body: { items: [{ id: 31, accountId: 1, fromAddress: "anna.berger@acme.com", fromName: "Anna Berger", subject: "Interview invitation",
          snippet: "See you on Monday", receivedAt: "2026-10-06T08:00:00Z", read: true, hasAttachments: false, processingState: "DONE",
          labels: [{ labelId: 3, slug: "jobs", name: "Jobs", color: "#00ff00", source: "AI" }] }],
          page: 0, size: 25, totalElements: 1, totalPages: 1 },
      },
    });

    renderWithProviders(<RecordDetail collectionId={1} recordId={5} />);
    await userEvent.click(await screen.findByRole("tab", { name: /Emails/ }));
    const panel = screen.getByRole("tabpanel", { name: /Emails/ });

    const list = await within(panel).findByRole("region", { name: "Email list" });
    const row = await within(list).findByRole("button", { name: /Interview invitation/ });
    expect(within(row).getByText("Jobs")).toBeInTheDocument();
    await waitFor(() => expect(row.querySelector("img")).toHaveAttribute("src", expect.stringContaining("/api/senders/logos/acme.com")));
    expect(within(panel).getByRole("region", { name: "Email details" })).toHaveTextContent("Select an email");
  });

  it("pages through the record's emails with the pager at the end of the list", async () => {
    const requested: string[] = [];
    mockApi({
      "GET /api/collections/1": { body: people },
      "GET /api/collections/1/records/5": { body: anna },
      "GET /api/senders/contact-photos": { body: [] },
      "GET /api/senders/logos": { body: [] },
      "GET /api/collections/1/records/5/emails": ({ url }) => {
        requested.push(url.search);
        const pageIndex = Number(url.searchParams.get("page") ?? 0);
        return {
          body: { items: [{ id: 40 + pageIndex, accountId: 1, fromAddress: "anna.berger@acme.com", fromName: "Anna Berger",
            subject: `Mail on page ${pageIndex + 1}`, receivedAt: "2026-10-06T08:00:00Z", read: true, hasAttachments: false,
            processingState: "DONE", labels: [] }], page: pageIndex, size: 50, totalElements: 60, totalPages: 2 },
        };
      },
    });

    renderWithProviders(<RecordDetail collectionId={1} recordId={5} />);
    await userEvent.click(await screen.findByRole("tab", { name: /Emails/ }));
    const panel = screen.getByRole("tabpanel", { name: /Emails/ });
    const mail = await within(panel).findByText("Mail on page 1");

    const pager = within(panel).getByRole("navigation", { name: "Pages" });
    expect(pager).toHaveTextContent("Page 1 of 2 · 60 emails");
    expect(within(panel).getByTestId("email-list")).toContainElement(pager);
    expect(mail.compareDocumentPosition(pager) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();

    await userEvent.click(within(pager).getByRole("button", { name: /Next/ }));

    expect(await within(panel).findByText("Mail on page 2")).toBeInTheDocument();
    expect(requested.some((search) => search.includes("page=1"))).toBe(true);
  });

  it("describes each connection in words", async () => {
    mockApi({
      "GET /api/collections/1": { body: people },
      "GET /api/collections/1/records/5": { body: anna },
      "GET /api/senders/contact-photos": { body: [] },
      "GET /api/senders/logos": { body: [] },
      "GET /api/relations": {
        body: [
          { id: 3, relationTypeId: 1, fromCollectionId: 1, fromRecordId: 5, toCollectionId: 2, toRecordId: 8, attributes: { role: "Recruiter" },
            validFrom: "2025-03-01", sourceEmailId: 31, confidence: 0.9, createdBy: "AI" },
          { id: 4, relationTypeId: 1, fromCollectionId: 2, fromRecordId: 9, toCollectionId: 1, toRecordId: 5, attributes: {}, createdBy: "USER" },
        ],
      },
      "GET /api/relation-types": {
        body: [{ id: 1, slug: "works_at", name: "works at", inverseName: "employs", directed: true, attributesSchema: {}, createdBy: "USER" }],
      },
      "GET /api/collections": { body: [people, { ...people, id: 2, slug: "organizations", name: "Organizations", fields: [] }] },
      "GET /api/collections/2/records/8": { body: { ...anna, id: 8, collectionId: 2, title: "Acme GmbH", values: {} } },
      "GET /api/collections/2/records/9": { body: { ...anna, id: 9, collectionId: 2, title: "Globex", values: {} } },
    });

    renderWithProviders(<RecordDetail collectionId={1} recordId={5} />);

    await userEvent.click(await screen.findByRole("tab", { name: /Connections/ }));
    const panel = screen.getByRole("tabpanel", { name: /Connections/ });
    const acme = await within(panel).findByRole("link", { name: /Acme GmbH/ });
    await waitFor(() => expect(acme).toHaveTextContent("Anna Berger works at Acme GmbH"));
    expect(acme).toHaveTextContent("Role: Recruiter");
    expect(acme).toHaveTextContent("Since Mar 2025");
    expect(acme).toHaveTextContent("Found in an email");
    const globex = await within(panel).findByRole("link", { name: /Globex/ });
    await waitFor(() => expect(globex).toHaveTextContent("Globex works at Anna Berger"));
  });

  it("lists the connected records", async () => {
    mockApi({
      "GET /api/collections/1": { body: people },
      "GET /api/collections/1/records/5": { body: anna },
      "GET /api/senders/contact-photos": { body: [] },
      "GET /api/senders/logos": { body: [] },
      "GET /api/relations": {
        body: [{ id: 3, relationTypeId: 1, fromCollectionId: 1, fromRecordId: 5, toCollectionId: 2, toRecordId: 8, attributes: {}, createdBy: "AI" }],
      },
      "GET /api/relation-types": {
        body: [{ id: 1, slug: "works_at", name: "works at", inverseName: "employs", fromCollectionId: 1, toCollectionId: 2, directed: true,
          createdBy: "USER" }],
      },
      "GET /api/collections": { body: [people, { ...people, id: 2, slug: "organizations", name: "Organizations", fields: [] }] },
      "GET /api/collections/2/records/8": { body: { ...anna, id: 8, collectionId: 2, title: "Acme GmbH", values: {} } },
    });

    renderWithProviders(<RecordDetail collectionId={1} recordId={5} />);

    await userEvent.click(await screen.findByRole("tab", { name: /Connections/ }));
    const panel = screen.getByRole("tabpanel", { name: /Connections/ });
    expect(await within(panel).findByRole("link", { name: /Acme GmbH/ })).toHaveAttribute("href", "/collections/2/records/8");
    expect(within(panel).getByText("works at")).toBeInTheDocument();
    expect(within(panel).getByText("Organizations")).toBeInTheDocument();
    expect(within(panel).getByLabelText("Added by the assistant")).toBeInTheDocument();
  });

  it("marks only the connections the assistant created", async () => {
    mockApi({
      "GET /api/collections/1": { body: people },
      "GET /api/collections/1/records/5": { body: anna },
      "GET /api/senders/contact-photos": { body: [] },
      "GET /api/senders/logos": { body: [] },
      "GET /api/relations": {
        body: [
          { id: 3, relationTypeId: 1, fromCollectionId: 1, fromRecordId: 5, toCollectionId: 2, toRecordId: 8, attributes: {}, createdBy: "AI" },
          { id: 4, relationTypeId: 1, fromCollectionId: 1, fromRecordId: 5, toCollectionId: 2, toRecordId: 9, attributes: {}, createdBy: "USER" },
        ],
      },
      "GET /api/relation-types": {
        body: [{ id: 1, slug: "works_at", name: "works at", inverseName: "employs", directed: true, attributesSchema: {}, createdBy: "USER" }],
      },
      "GET /api/collections": { body: [people, { ...people, id: 2, slug: "organizations", name: "Organizations", fields: [] }] },
      "GET /api/collections/2/records/8": { body: { ...anna, id: 8, collectionId: 2, title: "Acme GmbH", values: {} } },
      "GET /api/collections/2/records/9": { body: { ...anna, id: 9, collectionId: 2, title: "Globex", values: {} } },
    });

    renderWithProviders(<RecordDetail collectionId={1} recordId={5} />);

    await userEvent.click(await screen.findByRole("tab", { name: /Connections/ }));
    const panel = screen.getByRole("tabpanel", { name: /Connections/ });
    const acme = await within(panel).findByRole("link", { name: /Acme GmbH/ });
    const globex = await within(panel).findByRole("link", { name: /Globex/ });
    expect(within(acme).getByLabelText("Added by the assistant")).toBeInTheDocument();
    expect(within(globex).queryByLabelText("Added by the assistant")).not.toBeInTheDocument();
  });

  it("shows a not-found message", async () => {
    mockApi({ "GET /api/collections/1": { body: people } });

    renderWithProviders(<RecordDetail collectionId={1} recordId={5} />);

    expect(await screen.findByRole("alert")).toBeInTheDocument();
  });
});
