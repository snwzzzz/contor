import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { OPEN_COMMAND_PALETTE_EVENT } from "@/lib/command-palette";
import { QuickActions } from "./QuickActions";

describe("QuickActions", () => {
  it("links the most used pages as icon tiles", () => {
    render(<QuickActions />);

    for (const [name, href] of [
      ["View mailbox", "/inbox"],
      ["Open chat", "/chat"],
      ["Calendar", "/calendar"],
      ["To-dos", "/tasks"],
      ["Review proposals", "/proposals"],
      ["Findings", "/findings"],
      ["New collection", "/collections/new"],
    ]) {
      expect(screen.getByRole("link", { name: new RegExp(`^${name}`) })).toHaveAttribute("href", href);
    }
  });

  it("opens the command palette for everything else", async () => {
    const listener = vi.fn();
    window.addEventListener(OPEN_COMMAND_PALETTE_EVENT, listener);
    render(<QuickActions />);

    await userEvent.click(screen.getByRole("button", { name: "More" }));

    expect(listener).toHaveBeenCalledOnce();
    window.removeEventListener(OPEN_COMMAND_PALETTE_EVENT, listener);
  });
});
