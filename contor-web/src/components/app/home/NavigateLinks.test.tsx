import { screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { resetNavigation } from "@/test/next-navigation";
import { renderWithProviders } from "@/test/render";
import { NavigateLinks } from "./NavigateLinks";

vi.mock("next/navigation", () => import("@/test/next-navigation"));

describe("NavigateLinks", () => {
  beforeEach(() => resetNavigation("/"));

  it("links the other parts of contor with a one-line description", () => {
    renderWithProviders(<NavigateLinks />);

    expect(screen.getByRole("heading", { level: 2, name: "Navigate" })).toBeInTheDocument();
    for (const [name, href] of [
      ["Collections", "/collections"],
      ["Proposals", "/proposals"],
      ["Findings", "/findings"],
      ["Relations", "/relations"],
      ["Queries", "/queries"],
      ["Rules & Labels", "/rules"],
      ["Chat", "/chat"],
    ]) {
      expect(screen.getByRole("link", { name: new RegExp(`^${name}`) })).toHaveAttribute("href", href);
    }
    // Settings open from the search bar or the account menu.
    expect(screen.queryByRole("link", { name: /^Settings/ })).not.toBeInTheDocument();
    expect(screen.getByText("Records the assistant keeps, grouped by type.")).toBeInTheDocument();
  });
});
