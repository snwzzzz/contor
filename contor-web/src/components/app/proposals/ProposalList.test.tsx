import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { mockApi, type MockResponse } from "@/test/api-mock";
import { navigationState, resetNavigation, router } from "@/test/next-navigation";
import { renderWithProviders } from "@/test/render";
import { ProposalList } from "./ProposalList";

vi.mock("next/navigation", () => import("@/test/next-navigation"));

function collection(id: number, slug: string, name: string) {
  return { id, slug, name, system: true, matchKeys: [], displayConfig: {}, schemaVersion: 1, createdBy: "USER", fields: [] };
}

const collections = [collection(3, "events", "Calendar"), collection(4, "tasks", "To-dos")];

const actions = [
  {
    id: 11,
    kind: "CREATE_RECORD",
    status: "PENDING",
    payload: { collectionId: 3, title: "Interview at Acme", values: { starts_at: "2026-10-09T07:30:00Z", location: "Acme HQ" } },
    confidence: 0.95,
    reasoning: "Invitation to an interview on Friday",
    importance: "URGENT",
    importanceScore: 85,
    importanceReason: "an appointment; due within 2 days; from one of your contacts",
    createdAt: "2026-10-08T12:00:00Z",
  },
  {
    id: 12,
    kind: "CREATE_RECORD",
    status: "PENDING",
    payload: { collectionId: 4, title: "Send certificates", values: { due_date: "2026-10-08" } },
    createdAt: "2026-10-08T12:00:00Z",
  },
  {
    id: 13,
    kind: "CREATE_COLLECTION",
    status: "PENDING",
    payload: { slug: "job_applications", name: "Job Applications", fields: [{ name: "Company" }] },
    createdAt: "2026-10-08T12:00:00Z",
  },
];

function actionsPage(items: unknown[]): (request: { url: URL }) => MockResponse {
  return ({ url }) => {
    expect(url.searchParams.get("group")).toBe("ACTIONS");
    return { body: { items, page: 0, size: 50, totalElements: items.length, totalPages: 1 } };
  };
}

describe("ProposalList", () => {
  afterEach(() => resetNavigation());

  it("looks for insights on request", async () => {
    const user = userEvent.setup();
    const calls = mockApi({
      "GET /api/proposals": actionsPage(actions),
      "GET /api/collections": { body: collections },
      "POST /api/ai/insights": { status: 202, body: { running: true } },
    });
    renderWithProviders(<ProposalList />);
    await screen.findByRole("article", { name: "Interview at Acme" });

    await user.click(screen.getByRole("button", { name: "Look for insights" }));

    await waitFor(() => expect(calls.some((call) => call.method === "POST" && call.path === "/api/ai/insights")).toBe(true));
    expect(await screen.findByRole("button", { name: /Looking for insights/ })).toBeDisabled();
  });

  it("marks important proposals and says why", async () => {
    mockApi({ "GET /api/proposals": actionsPage(actions), "GET /api/collections": { body: collections } });

    renderWithProviders(<ProposalList />);

    const urgent = await screen.findByRole("article", { name: "Interview at Acme" });
    const badge = within(urgent).getByText("Urgent");
    expect(badge).toHaveAttribute("title", "an appointment; due within 2 days; from one of your contacts");
    expect(within(screen.getByRole("article", { name: "Send certificates" })).queryByText(/Urgent|High/)).not.toBeInTheDocument();
  });

  it("shows each card's kind as a small bare icon with the label, and the reasoning as plain text", async () => {
    mockApi({ "GET /api/proposals": actionsPage(actions), "GET /api/collections": { body: collections } });
    renderWithProviders(<ProposalList />);

    const card = await screen.findByRole("article", { name: "Interview at Acme" });
    // No icon on a colored tile and no gray box: the kind is a small bare icon next to its label.
    expect(card.querySelector("[class*='bg-tint']")).toBeNull();
    expect([...card.querySelectorAll("*")].some((element) => element.classList.contains("bg-muted/50"))).toBe(false);
    expect(card.querySelector("svg[class*='size-3.5']")).not.toBeNull();
    expect(within(card).getByText("Invitation to an interview on Friday")).toHaveClass("text-muted-foreground");
  });

  it("explains the importance in the detail sidebar", async () => {
    const user = userEvent.setup();
    mockApi({ "GET /api/proposals": actionsPage(actions), "GET /api/collections": { body: collections } });
    renderWithProviders(<ProposalList />);

    await user.click(await screen.findByRole("button", { name: "Show details for Interview at Acme" }));

    const sheet = await screen.findByRole("dialog", { name: "Interview at Acme" });
    const section = within(sheet).getByRole("region", { name: "Importance" });
    expect(section).toHaveTextContent("Urgent");
    expect(section).toHaveTextContent("an appointment; due within 2 days; from one of your contacts");
  });

  it("filters to important proposals, sorts by importance and keeps both in the URL", async () => {
    const user = userEvent.setup();
    navigationState.pathname = "/proposals";
    const requested: URLSearchParams[] = [];
    mockApi({
      "GET /api/proposals": ({ url }) => {
        requested.push(url.searchParams);
        return actionsPage(actions)({ url });
      },
      "GET /api/collections": { body: collections },
    });
    renderWithProviders(<ProposalList />);
    await screen.findByRole("article", { name: "Interview at Acme" });

    await user.click(screen.getByRole("button", { name: "High and up" }));
    await user.click(screen.getByRole("button", { name: "Most important first" }));

    await waitFor(() => {
      const last = requested.at(-1);
      expect(last?.get("minImportance")).toBe("HIGH");
      expect(last?.get("sort")).toBe("IMPORTANCE");
    });
    expect(router.replace).toHaveBeenLastCalledWith("/proposals?importance=high&sort=importance", { scroll: false });
    expect(screen.getByRole("button", { name: "High and up" })).toHaveAttribute("aria-pressed", "true");
  });

  it("starts with the importance filter from the URL", async () => {
    navigationState.search = "?importance=urgent";
    const requested: URLSearchParams[] = [];
    mockApi({
      "GET /api/proposals": ({ url }) => {
        requested.push(url.searchParams);
        return actionsPage(actions)({ url });
      },
      "GET /api/collections": { body: collections },
    });

    renderWithProviders(<ProposalList />);

    await waitFor(() => expect(requested[0]?.get("minImportance")).toBe("URGENT"));
    expect(await screen.findByRole("button", { name: "Urgent" })).toHaveAttribute("aria-pressed", "true");
  });

  it("says when nothing important is waiting and offers to show everything", async () => {
    const user = userEvent.setup();
    navigationState.search = "?importance=high";
    mockApi({
      "GET /api/proposals": ({ url }) => actionsPage(url.searchParams.get("minImportance") ? [] : actions)({ url }),
      "GET /api/collections": { body: collections },
    });
    renderWithProviders(<ProposalList />);

    expect(await screen.findByText("Nothing this important is waiting")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Show all proposals" }));

    expect(await screen.findByRole("article", { name: "Interview at Acme" })).toBeInTheDocument();
  });

  it("stretches the cards of a row to the same height, buttons at the bottom", async () => {
    mockApi({ "GET /api/proposals": actionsPage(actions), "GET /api/collections": { body: collections } });

    renderWithProviders(<ProposalList />);

    const card = await screen.findByRole("article", { name: "Interview at Acme" });
    expect(card).toHaveClass("h-full");
    expect(card).toHaveClass("hover:shadow-md");
    expect(card.parentElement).not.toHaveClass("items-start");
    expect(within(card).getByRole("button", { name: "Approve Interview at Acme" }).parentElement).toHaveClass("mt-auto");
  });

  it("lists the proposed actions with what each would do", async () => {
    mockApi({ "GET /api/proposals": actionsPage(actions), "GET /api/collections": { body: collections } });

    renderWithProviders(<ProposalList />);

    const interview = await screen.findByRole("article", { name: "Interview at Acme" });
    expect(await within(interview).findByText("Add to calendar")).toBeInTheDocument();
    expect(within(interview).getByText(/Acme HQ/)).toBeInTheDocument();
    expect(within(interview).getByText("Invitation to an interview on Friday")).toBeInTheDocument();
    expect(within(interview).getByText("95%")).toBeInTheDocument();
    expect(within(screen.getByRole("article", { name: "Send certificates" })).getByText("New to-do")).toBeInTheDocument();
    expect(within(screen.getByRole("article", { name: "Job Applications" })).getByText("New category")).toBeInTheDocument();
  });

  it("filters by what the proposal would do", async () => {
    mockApi({ "GET /api/proposals": actionsPage(actions), "GET /api/collections": { body: collections } });

    renderWithProviders(<ProposalList />);
    await screen.findByRole("article", { name: "Interview at Acme" });

    expect(screen.getByRole("button", { name: "All 3" })).toHaveAttribute("aria-pressed", "true");
    await userEvent.click(await screen.findByRole("button", { name: "To-dos 1" }));

    expect(screen.getAllByRole("article").map((card) => card.getAttribute("aria-label"))).toEqual(["Send certificates"]);
    await userEvent.click(screen.getByRole("button", { name: "All 3" }));
    expect(screen.getAllByRole("article")).toHaveLength(3);
  });

  it("approves and rejects", async () => {
    const calls = mockApi({
      "GET /api/proposals": actionsPage(actions),
      "GET /api/collections": { body: collections },
      "POST /api/proposals/11/decision": { body: { ...actions[0], status: "APPROVED" } },
      "POST /api/proposals/12/decision": { body: { ...actions[1], status: "REJECTED" } },
    });

    renderWithProviders(<ProposalList />);

    await userEvent.click(await screen.findByRole("button", { name: "Approve Interview at Acme" }));
    await userEvent.click(screen.getByRole("button", { name: "Reject Send certificates" }));

    await waitFor(() => {
      expect(calls.find((call) => call.path === "/api/proposals/11/decision")?.body).toEqual({ decision: "APPROVE" });
      expect(calls.find((call) => call.path === "/api/proposals/12/decision")?.body).toEqual({ decision: "REJECT" });
    });
  });

  const reply = {
    id: 21,
    kind: "DRAFT_REPLY",
    status: "PENDING",
    payload: {
      emailId: 8,
      accountId: 3,
      conversationId: "conv",
      to: [{ name: "Anna Berger", address: "anna@acme.com" }],
      cc: [],
      subject: "Re: Workshop",
      body: "Hallo Anna,\n\nich komme am [Tag].\n\nMax",
      language: "de",
      placeholders: ["[Tag]"],
    },
    reasoning: "Proposes a day",
    sourceEmailId: 8,
    createdAt: "2026-10-08T12:00:00Z",
  };

  it("shows a reply draft with recipients, subject and highlighted placeholders", async () => {
    mockApi({ "GET /api/proposals": actionsPage([reply]), "GET /api/collections": { body: collections } });

    renderWithProviders(<ProposalList />);

    const card = await screen.findByRole("article", { name: "Re: Workshop" });
    expect(within(card).getByText("Draft reply")).toBeInTheDocument();
    expect(within(card).getByText("Anna Berger <anna@acme.com>")).toBeInTheDocument();
    expect(within(card).getByText("[Tag]").tagName).toBe("MARK");
    expect(screen.getByRole("button", { name: /Replies/ })).toBeInTheDocument();
  });

  it("keeps an edited reply draft by approving it with the edited payload", async () => {
    const calls = mockApi({
      "GET /api/proposals": actionsPage([reply]),
      "GET /api/collections": { body: collections },
      "POST /api/proposals/21/decision": { body: { ...reply, status: "EDITED_AND_APPROVED" } },
    });
    renderWithProviders(<ProposalList />);

    await userEvent.click(await screen.findByRole("button", { name: "Edit & keep Re: Workshop" }));
    const text = await screen.findByRole("textbox", { name: "Text" });
    await userEvent.clear(text);
    await userEvent.type(text, "Hallo Anna, ich komme am Freitag. Max");
    await userEvent.click(screen.getByRole("button", { name: "Keep" }));

    await waitFor(() =>
      expect(calls.find((call) => call.path === "/api/proposals/21/decision")?.body).toEqual({
        decision: "APPROVE",
        payload: { ...reply.payload, body: "Hallo Anna, ich komme am Freitag. Max", placeholders: [] },
      }),
    );
  });

  it("dismisses a reply draft", async () => {
    const calls = mockApi({
      "GET /api/proposals": actionsPage([reply]),
      "GET /api/collections": { body: collections },
      "POST /api/proposals/21/decision": { body: { ...reply, status: "REJECTED" } },
    });
    renderWithProviders(<ProposalList />);

    await userEvent.click(await screen.findByRole("button", { name: "Dismiss Re: Workshop" }));

    await waitFor(() => expect(calls.find((call) => call.path === "/api/proposals/21/decision")?.body).toEqual({ decision: "REJECT" }));
  });

  describe("detail sidebar", () => {
    const detailed = {
      id: 31,
      kind: "CREATE_RECORD",
      status: "PENDING",
      payload: {
        collectionId: 3,
        title: "Workshop day",
        values: { location: "Acme HQ", room: "Room 4", organizer: "Anna", dress_code: "Casual", notes: "Bring a laptop" },
      },
      confidence: 0.8,
      reasoning: "The mail announces a workshop day with room and dress code.",
      model: "qwen3:8b",
      promptVersion: "extract-v3",
      sourceEmailId: 5,
      createdAt: "2026-10-08T12:00:00Z",
    };
    const email = {
      id: 5,
      accountId: 1,
      fromName: "Anna Berger",
      fromAddress: "anna@acme.com",
      subject: "Workshop on Friday",
      snippet: "Hi, see you on Friday",
      bodyText: "Hi, see you on Friday at the office.",
      receivedAt: "2026-10-07T09:00:00Z",
      toRecipients: [],
      ccRecipients: [],
      labels: [],
    };

    it("opens from the card and shows every value, the reasoning and the source email", async () => {
      mockApi({
        "GET /api/proposals": actionsPage([detailed]),
        "GET /api/collections": { body: collections },
        "GET /api/emails/5": { body: email },
      });
      renderWithProviders(<ProposalList />);

      await userEvent.click(await screen.findByRole("button", { name: "Show details for Workshop day" }));

      const sheet = await screen.findByRole("dialog", { name: "Workshop day" });
      expect(within(sheet).getByText("Bring a laptop")).toBeInTheDocument();
      // Long values (URLs) wrap anywhere instead of widening the sheet.
      expect(within(sheet).getByText("Bring a laptop")).toHaveClass("[overflow-wrap:anywhere]");
      expect(within(sheet).getByText("Casual")).toBeInTheDocument();
      expect(within(sheet).getByText("The mail announces a workshop day with room and dress code.")).toBeInTheDocument();
      expect(within(sheet).getByText(/qwen3:8b/)).toBeInTheDocument();
      expect(await within(sheet).findByText("Workshop on Friday")).toBeInTheDocument();
      expect(within(sheet).getByText("Anna Berger")).toHaveClass("truncate");
      expect(within(sheet).queryByText("Hi, see you on Friday at the office.")).not.toBeInTheDocument();

      await userEvent.click(within(sheet).getByRole("button", { name: "Show email" }));
      expect(await within(sheet).findByText("Hi, see you on Friday at the office.")).toBeInTheDocument();
      expect(within(sheet).getByRole("link", { name: "Open in inbox" })).toHaveAttribute("href", "/inbox?email=5");
    });

    it("shows before and after for an update and hides unchanged values", async () => {
      const update = {
        id: 32,
        kind: "UPDATE_RECORD",
        status: "PENDING",
        payload: { collectionId: 3, recordId: 7, title: "Interview", values: { location: "New HQ", notes: "Same notes" } },
        createdAt: "2026-10-08T12:00:00Z",
      };
      mockApi({
        "GET /api/proposals": actionsPage([update]),
        "GET /api/collections": { body: collections },
        "GET /api/collections/3/records/7": {
          body: { id: 7, collectionId: 3, title: "Interview", values: { location: "Old HQ", notes: "Same notes" }, extra: {}, createdBy: "AI", createdAt: "2026-10-01T00:00:00Z", lastModified: "2026-10-01T00:00:00Z" },
        },
      });
      renderWithProviders(<ProposalList />);

      await userEvent.click(await screen.findByRole("button", { name: "Show details for Interview" }));

      const sheet = await screen.findByRole("dialog", { name: "Interview" });
      expect(await within(sheet).findByText("Old HQ")).toBeInTheDocument();
      expect(within(sheet).getByText("New HQ")).toBeInTheDocument();
      expect(within(sheet).queryByText("Same notes")).not.toBeInTheDocument();
    });

    it("edits the values before approving and sends the edited payload", async () => {
      const textField = (id: number, slug: string, name: string, type: string) => ({ id, slug, name, type, options: {}, required: false, uniqueValue: false, position: id });
      const calendar = { ...collection(3, "events", "Calendar"), fields: [textField(1, "location", "Location", "TEXT"), textField(2, "notes", "Notes", "LONG_TEXT")] };
      const calls = mockApi({
        "GET /api/proposals": actionsPage([detailed]),
        "GET /api/collections": { body: [calendar] },
        "GET /api/emails/5": { body: email },
        "POST /api/proposals/31/decision": { body: { ...detailed, status: "EDITED_AND_APPROVED" } },
      });
      renderWithProviders(<ProposalList />);
      await userEvent.click(await screen.findByRole("button", { name: "Show details for Workshop day" }));
      const sheet = await screen.findByRole("dialog", { name: "Workshop day" });

      await userEvent.click(within(sheet).getByRole("button", { name: "Edit" }));
      const location = within(sheet).getByRole("textbox", { name: "Location" });
      await userEvent.clear(location);
      await userEvent.type(location, "Acme Tower");
      const title = within(sheet).getByRole("textbox", { name: "Title" });
      await userEvent.clear(title);
      await userEvent.type(title, "Workshop");
      // Values without a field of the collection stay as they are, shown as text.
      expect(within(sheet).getByText("Room 4")).toBeInTheDocument();
      await userEvent.click(within(sheet).getByRole("button", { name: "Approve" }));

      await waitFor(() =>
        expect(calls.find((call) => call.path === "/api/proposals/31/decision")?.body).toEqual({
          decision: "APPROVE",
          payload: { ...detailed.payload, title: "Workshop", values: { ...detailed.payload.values, location: "Acme Tower" } },
        }),
      );
    });

    it("approves from the sidebar and closes it", async () => {
      const calls = mockApi({
        "GET /api/proposals": actionsPage([detailed]),
        "GET /api/collections": { body: collections },
        "GET /api/emails/5": { body: email },
        "POST /api/proposals/31/decision": { body: { ...detailed, status: "APPROVED" } },
      });
      renderWithProviders(<ProposalList />);
      await userEvent.click(await screen.findByRole("button", { name: "Show details for Workshop day" }));
      const sheet = await screen.findByRole("dialog", { name: "Workshop day" });

      await userEvent.click(within(sheet).getByRole("button", { name: "Approve" }));

      await waitFor(() => expect(calls.find((call) => call.path === "/api/proposals/31/decision")?.body).toEqual({ decision: "APPROVE" }));
      await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    });
  });

  it("says when there is nothing to do", async () => {
    mockApi({ "GET /api/proposals": actionsPage([]), "GET /api/collections": { body: collections } });

    renderWithProviders(<ProposalList />);

    expect(await screen.findByText("Nothing to review")).toBeInTheDocument();
  });
  it("names the app a record proposal comes from", async () => {
    mockApi({
      "GET /api/proposals": actionsPage([
        {
          id: 22,
          kind: "CREATE_RECORD",
          status: "PENDING",
          payload: { collectionId: 4, title: "SO-155", values: {} },
          sourceApp: { apiId: 7, apiName: "Shop", endpointName: "Orders" },
          createdAt: "2026-10-29T12:00:00Z",
        },
      ]),
      "GET /api/collections": { body: collections },
    });

    renderWithProviders(<ProposalList />);

    const card = await screen.findByRole("article", { name: "SO-155" });
    expect(within(card).getByRole("link", { name: "From Shop · Orders" })).toHaveAttribute("href", "/apps/7");
  });

  it("shows where an app's items would go and which data fills which field", async () => {
    const people = { ...collection(5, "people", "People"), system: false };
    mockApi({
      "GET /api/proposals": actionsPage([
        {
          id: 21,
          kind: "MAP_API_ENDPOINT",
          status: "PENDING",
          payload: {
            mappingId: 40,
            endpointId: 30,
            collectionId: 5,
            title: "CRM · Contacts",
            titlePath: "$.name",
            fields: [{ fieldSlug: "full_name", path: "$.name" }, { fieldSlug: "email", path: "$.mail" }],
          },
          reasoning: "CRM contacts are people",
          createdAt: "2026-10-29T12:00:00Z",
        },
      ]),
      "GET /api/collections": { body: [...collections, people] },
    });

    renderWithProviders(<ProposalList />);

    const card = await screen.findByRole("article", { name: "CRM · Contacts" });
    expect(within(card).getByText("Connect app data")).toBeInTheDocument();
    expect(within(card).getByText("Into")).toBeInTheDocument();
    expect(within(card).getByText("People")).toBeInTheDocument();
    expect(within(card).getByText("Full name")).toBeInTheDocument();
    expect(within(card).getByText("← $.mail")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Categories/ })).toBeInTheDocument();
  });
});
