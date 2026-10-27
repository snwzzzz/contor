import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { apiError, mockApi } from "@/test/api-mock";
import { resetNavigation, router } from "@/test/next-navigation";
import { renderWithProviders } from "@/test/render";
import { ChatThread } from "./ChatThread";

vi.mock("next/navigation", () => import("@/test/next-navigation"));

const READY = { reachable: true, ready: true, models: [] };
const NOW = "2026-10-27T10:00:00Z";

const QUESTION = { id: 10, role: "USER", status: "DONE", text: "Who is Anna Kessler?", blocks: [], sources: [], mentions: [], createdAt: NOW };

const ANSWER = {
  id: 11, role: "ASSISTANT", status: "DONE", text: "Anna Kessler works at Kessler GmbH.", createdAt: NOW,
  blocks: [
    { id: 0, type: "PARAGRAPH", columns: [], rows: [], items: [{ id: 0, text: "Anna Kessler works at Kessler GmbH.", check: "SUPPORTED", markers: [1] }] },
    { id: 1, type: "BULLETS", columns: [], rows: [], items: [{ id: 0, text: "She wrote about a late delivery.", check: "SUPPORTED", markers: [2] }] },
  ],
  sources: [
    { marker: 1, kind: "FIELD", label: "Company", collectionId: 3, recordId: 4711, recordTitle: "Anna Kessler", fieldSlug: "company",
      fieldName: "Company", value: "Kessler GmbH", sourceState: "CURRENT" },
    { marker: 2, kind: "EMAIL_PARAGRAPH", label: "Delay notice", emailId: 9, emailSubject: "Delay notice", emailFrom: "Anna Kessler",
      paragraphIndex: 1, quote: "the delivery is late", sourceState: "CURRENT" },
  ],
  mentions: [
    { type: "RECORD", collectionId: 3, recordId: 4711, label: "Anna Kessler", collectionName: "People" },
    { type: "EMAIL", emailId: 9, label: "Delay notice" },
  ],
};

function conversation(answer: Record<string, unknown> = ANSWER) {
  return { id: 5, title: "Who is Anna Kessler?", createdAt: NOW, lastModified: NOW, messages: [QUESTION, answer] };
}

function api(extra: Record<string, unknown> = {}) {
  return mockApi({
    "GET /api/ai/status": { body: READY },
    "GET /api/chat/conversations": { body: [] },
    "GET /api/chat/conversations/5": { body: conversation() },
    ...extra,
  });
}

function renderThread() {
  return renderWithProviders(<ChatThread conversationId={5} />);
}

describe("ChatThread", () => {
  beforeEach(() => resetNavigation("/chat/5"));

  it("shows the question as the user's message and the answer with its source markers", async () => {
    api();
    renderThread();

    expect(await screen.findByRole("heading", { level: 2, name: "Who is Anna Kessler?" })).toBeInTheDocument();
    expect(within(screen.getByRole("region", { name: "Your question" })).getByText("Who is Anna Kessler?")).toBeInTheDocument();
    const answer = screen.getByRole("region", { name: "Assistant's answer" });
    expect(within(answer).getByText("Anna Kessler works at Kessler GmbH.")).toBeInTheDocument();
    expect(within(answer).getByText("She wrote about a late delivery.").closest("li")).not.toBeNull();
    expect(within(answer).getByRole("button", { name: /Source 1/ })).toBeInTheDocument();
  });

  it("keeps the follow-up input at the bottom, also while the conversation is short", async () => {
    api();
    renderThread();

    const input = await screen.findByRole("textbox", { name: "Ask a follow-up" });
    const bar = input.closest("[data-slot=chat-composer]");
    // The conversation fills the detail area, so the bar is pushed down (mt-auto) and sticks there while scrolling.
    expect(bar).toHaveClass("mt-auto", "sticky");
    expect(screen.getByRole("article")).toHaveClass("flex-1", "flex-col");
    expect(bar?.parentElement).toHaveClass("flex", "flex-col");
  });

  it("links the people, records and emails an answer is about", async () => {
    api();
    renderThread();

    const mentions = await screen.findByRole("list", { name: "Mentioned" });
    // Records open in the sidebar over the chat; emails in the inbox.
    expect(within(mentions).getByRole("link", { name: /Anna Kessler/ })).toHaveAttribute("href", "/chat/5?record=3-4711");
    expect(within(mentions).getByRole("link", { name: /Anna Kessler/ })).toHaveTextContent("People");
    expect(within(mentions).getByRole("link", { name: /Delay notice/ })).toHaveAttribute("href", "/inbox?email=9");
  });

  it("opens a cited source in the sources panel", async () => {
    api();
    renderThread();
    const answer = await screen.findByRole("region", { name: "Assistant's answer" });

    await userEvent.click(within(answer).getByRole("button", { name: /Source 2/ }));

    const sources = screen.getByRole("complementary", { name: "Sources" });
    expect(within(sources).getByRole("button", { name: /Delay notice/ })).toHaveAttribute("aria-expanded", "true");
  });

  it("shows what the assistant is doing while it answers, and waits with the next question", async () => {
    api({ "GET /api/chat/conversations/5": { body: conversation({ ...ANSWER, status: "RETRIEVING", text: undefined, blocks: [], sources: [], mentions: [] }) } });
    renderThread();

    expect((await screen.findByText("Looking up records and emails…")).closest("[role=status]")).not.toBeNull();
    expect(screen.getByRole("textbox", { name: "Ask a follow-up" })).toBeDisabled();
    expect(screen.getByText("Wait until the answer is ready.")).toBeInTheDocument();
  });

  it("shows why an answer failed and writes it again on request", async () => {
    const calls = api({
      "GET /api/chat/conversations/5": { body: conversation({ ...ANSWER, status: "FAILED", error: "The model did not answer.", text: undefined, blocks: [] }) },
      "POST /api/chat/conversations/5/messages/11/retry": { status: 202, body: conversation({ ...ANSWER, status: "PENDING", blocks: [] }) },
    });
    renderThread();

    expect(await screen.findByRole("alert")).toHaveTextContent("The model did not answer.");
    await userEvent.click(screen.getByRole("button", { name: "Try again" }));

    await vi.waitFor(() => expect(calls.some((call) => call.method === "POST" && call.path.endsWith("/retry"))).toBe(true));
    expect((await screen.findByText("Waiting for the assistant…")).closest("[role=status]")).not.toBeNull();
  });

  it("asks a follow-up question in the same conversation", async () => {
    const calls = api({
      "POST /api/chat/conversations/5/messages": { status: 202, body: conversation() },
    });
    renderThread();
    const input = await screen.findByRole("textbox", { name: "Ask a follow-up" });

    await userEvent.type(input, "And where does she work?{Enter}");

    await vi.waitFor(() => expect(calls.find((call) => call.method === "POST")?.body).toEqual({ text: "And where does she work?" }));
  });

  it("renames the conversation", async () => {
    const calls = api({
      "PATCH /api/chat/conversations/5": { body: { ...conversation(), title: "Anna" } },
    });
    renderThread();
    await userEvent.click(await screen.findByRole("button", { name: "Rename" }));
    const title = screen.getByRole("textbox", { name: "Title" });

    await userEvent.clear(title);
    await userEvent.type(title, "Anna{Enter}");

    await vi.waitFor(() => expect(calls.find((call) => call.method === "PATCH")?.body).toEqual({ title: "Anna" }));
    expect(await screen.findByRole("heading", { level: 2, name: "Anna" })).toBeInTheDocument();
  });

  it("deletes the conversation after a confirmation and goes back to a new chat", async () => {
    const calls = api({ "DELETE /api/chat/conversations/5": { status: 204 } });
    renderThread();

    await userEvent.click(await screen.findByRole("button", { name: "Delete" }));
    expect(calls.some((call) => call.method === "DELETE")).toBe(false);
    await userEvent.click(screen.getByRole("button", { name: "Delete conversation" }));

    await vi.waitFor(() => expect(router.push).toHaveBeenCalledWith("/chat"));
    expect(calls.some((call) => call.method === "DELETE" && call.path === "/api/chat/conversations/5")).toBe(true);
  });

  it("says when the conversation does not exist", async () => {
    api({ "GET /api/chat/conversations/5": apiError(404, "Chat conversation not found with id: 5") });
    renderThread();

    expect(await screen.findByRole("alert")).toHaveTextContent("This conversation does not exist (anymore).");
  });
});
