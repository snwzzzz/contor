import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { QuickActions } from "./QuickActions";

describe("QuickActions", () => {
  it("links the most used pages", () => {
    render(<QuickActions />);

    for (const [name, href] of [
      ["View mailbox", "/inbox"],
      ["Open chat", "/chat"],
      ["Calendar", "/calendar"],
      ["To-dos", "/tasks"],
      ["Review proposals", "/proposals"],
    ]) {
      expect(screen.getByRole("link", { name: new RegExp(`^${name}`) })).toHaveAttribute("href", href);
    }
  });
});
