import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { mockApi } from "@/test/api-mock";
import { resetNavigation, router } from "@/test/next-navigation";
import { renderWithProviders } from "@/test/render";
import { ChatWorkspace } from "./ChatWorkspace";

vi.mock("next/navigation", () => import("@/test/next-navigation"));

const READY = { reachable: true, ready: true, models: [] };
const NOW = "2026-10-27T10:00:00Z";

const LIST = [
  { id: 5, title: "Who is Anna Kessler?", answering: false, createdAt: NOW, lastModified: NOW },
  { id: 3, title: "Top customers this year", answering: true, createdAt: NOW, lastModified: NOW },
];

function conversation(id: number, title: string) {
  return {
    id, title, createdAt: NOW, lastModified: NOW,
    messages: [
      { id: 10, role: "USER", status: "DONE", text: title, blocks: [], sources: [], mentions: [], createdAt: NOW },
      { id: 11, role: "ASSISTANT", status: "PENDING", blocks: [], sources: [], mentions: [], createdAt: NOW },
    ],
  };
}

function api(extra: Record<string, unknown> = {}) {
  return mockApi({
    "GET /api/ai/status": { body: READY },
    "GET /api/chat/conversations": { body: LIST },
    "GET /api/chat/conversations/5": { body: conversation(5, "Who is Anna Kessler?") },
    "POST /api/chat/conversations": { status: 201, body: conversation(7, "Which complaints are still open?") },
    ...extra,
  });
}

describe("ChatWorkspace", () => {
  beforeEach(() => resetNavigation("/chat"));

  it("starts with the question, the input and ready-made questions, the conversations in a side list", async () => {
    api();
    renderWithProviders(<ChatWorkspace />);

    expect(screen.getByRole("heading", { name: "What do you want to know?" })).toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: "Ask the assistant" })).toBeInTheDocument();
    expect(within(screen.getByRole("list", { name: "Suggestions" })).getAllByRole("button").length).toBeGreaterThan(0);
    const list = screen.getByRole("complementary", { name: "Conversations" });
    expect(await within(list).findByRole("link", { name: /Who is Anna Kessler\?/ })).toHaveAttribute("href", "/chat/5");
    // A conversation still being answered says so instead of its date.
    expect(within(list).getByRole("link", { name: /Top customers this year/ })).toHaveTextContent("Writing…");
    expect(within(list).getByRole("link", { name: "New chat" })).toHaveAttribute("href", "/chat");
  });

  it("fills the rest of the screen, the list and the detail scroll on their own", () => {
    api();
    renderWithProviders(<ChatWorkspace />);

    const workspace = screen.getByRole("region", { name: "Chat" });
    expect(workspace).toHaveClass("lg:flex-1", "lg:min-h-0");
    expect(workspace.className).not.toMatch(/100dvh|100svh/);
    expect(screen.getByRole("complementary", { name: "Conversations" })).toHaveClass("lg:border-r");
    expect(screen.getByTestId("chat-detail")).toHaveClass("lg:overflow-y-auto", "lg:overflow-x-hidden");
  });

  it("says when there are no conversations yet", async () => {
    api({ "GET /api/chat/conversations": { body: [] } });
    renderWithProviders(<ChatWorkspace />);

    expect(await screen.findByText("No conversations yet")).toBeInTheDocument();
  });

  it("sends the first question and opens the new conversation", async () => {
    const calls = api();
    renderWithProviders(<ChatWorkspace />);

    await userEvent.type(screen.getByRole("textbox", { name: "Ask the assistant" }), "Which complaints are still open?{Enter}");

    await vi.waitFor(() => expect(router.push).toHaveBeenCalledWith("/chat/7"));
    expect(calls.find((call) => call.method === "POST")?.body).toEqual({ text: "Which complaints are still open?" });
  });

  it("sends a ready-made question with one click", async () => {
    const calls = api();
    renderWithProviders(<ChatWorkspace />);
    const suggestion = within(screen.getByRole("list", { name: "Suggestions" })).getAllByRole("button")[0];

    await userEvent.click(suggestion);

    await vi.waitFor(() => expect(router.push).toHaveBeenCalledWith("/chat/7"));
    expect(calls.find((call) => call.method === "POST")?.body).toEqual({ text: suggestion.textContent });
  });

  it("cannot ask while the AI model is not ready", async () => {
    api({ "GET /api/ai/status": { body: { reachable: false, ready: false, models: [] } } });
    renderWithProviders(<ChatWorkspace />);

    expect(await screen.findByText(/The AI model is not ready/)).toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: "Ask the assistant" })).toBeDisabled();
    for (const button of within(screen.getByRole("list", { name: "Suggestions" })).getAllByRole("button")) {
      expect(button).toBeDisabled();
    }
  });

  it("shows an open conversation next to the list, marked in it", async () => {
    resetNavigation("/chat/5");
    api();
    renderWithProviders(<ChatWorkspace conversationId={5} />);

    expect(await screen.findByRole("heading", { level: 2, name: "Who is Anna Kessler?" })).toBeInTheDocument();
    const list = screen.getByRole("complementary", { name: "Conversations" });
    expect(await within(list).findByRole("link", { name: /Who is Anna Kessler\?/ })).toHaveAttribute("aria-current", "page");
    expect(within(list).getByRole("link", { name: /Top customers/ })).not.toHaveAttribute("aria-current");
    expect(screen.queryByRole("heading", { name: "What do you want to know?" })).not.toBeInTheDocument();
  });
});
