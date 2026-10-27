import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { mockApi } from "@/test/api-mock";
import { resetNavigation, router } from "@/test/next-navigation";
import { renderWithProviders } from "@/test/render";
import { HomeChat } from "./HomeChat";

vi.mock("next/navigation", () => import("@/test/next-navigation"));

const NOW = "2026-10-27T10:00:00Z";

const CREATED = {
  id: 7, title: "Who is Anna Kessler?", createdAt: NOW, lastModified: NOW,
  messages: [
    { id: 10, role: "USER", status: "DONE", text: "Who is Anna Kessler?", blocks: [], sources: [], mentions: [], createdAt: NOW },
    { id: 11, role: "ASSISTANT", status: "PENDING", blocks: [], sources: [], mentions: [], createdAt: NOW },
  ],
};

function api(ready = true) {
  return mockApi({
    "GET /api/ai/status": { body: { reachable: ready, ready, models: [] } },
    "POST /api/chat/conversations": { status: 201, body: CREATED },
  });
}

describe("HomeChat", () => {
  beforeEach(() => resetNavigation("/"));

  it("asks the assistant from the home page and opens the new conversation", async () => {
    const calls = api();
    renderWithProviders(<HomeChat />);

    await userEvent.type(screen.getByRole("textbox", { name: "Ask the assistant" }), "Who is Anna Kessler?{Enter}");

    await vi.waitFor(() => expect(router.push).toHaveBeenCalledWith("/chat/7"));
    expect(calls.find((call) => call.method === "POST")?.body).toEqual({ text: "Who is Anna Kessler?" });
  });

  it("is a full-width section with a heading, a line of text and a link to the earlier conversations", () => {
    api();
    renderWithProviders(<HomeChat />);

    const section = screen.getByRole("region", { name: "What do you want to know?" });
    expect(section.className).not.toMatch(/max-w-/);
    expect(within(section).getByText(/Answers link to what they are about/)).toBeInTheDocument();
    expect(within(section).getByRole("link", { name: /All chats/ })).toHaveAttribute("href", "/chat");
  });

  it("sends a ready-made question with one click", async () => {
    const calls = api();
    renderWithProviders(<HomeChat />);
    const suggestion = within(screen.getByRole("list", { name: "Suggestions" })).getAllByRole("button")[0];

    await userEvent.click(suggestion);

    await vi.waitFor(() => expect(router.push).toHaveBeenCalledWith("/chat/7"));
    expect(calls.find((call) => call.method === "POST")?.body).toEqual({ text: suggestion.textContent });
  });

  it("cannot ask while the AI model is not ready", async () => {
    api(false);
    renderWithProviders(<HomeChat />);

    expect(await screen.findByText(/The AI model is not ready/)).toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: "Ask the assistant" })).toBeDisabled();
    for (const button of within(screen.getByRole("list", { name: "Suggestions" })).getAllByRole("button")) {
      expect(button).toBeDisabled();
    }
  });
});
