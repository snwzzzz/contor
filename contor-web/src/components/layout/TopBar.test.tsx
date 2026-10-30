import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { mockApi } from "@/test/api-mock";
import { resetNavigation } from "@/test/next-navigation";
import { renderWithProviders } from "@/test/render";
import { TopBar } from "./TopBar";

vi.mock("next/navigation", () => import("@/test/next-navigation"));
vi.mock("next-themes", () => ({ useTheme: () => ({ theme: "system", setTheme: vi.fn() }) }));

function renderTopBar() {
  return renderWithProviders(<TopBar />);
}

describe("TopBar", () => {
  beforeEach(() => {
    resetNavigation("/calendar");
    mockApi({
      "GET /api/auth/me": { body: { id: 1, username: "owner", email: "owner@example.com", role: "OWNER", totpEnabled: false } },
      "GET /api/external-apis": { body: [] },
    });
  });

  it("opens the app launcher, empty until an app is added", async () => {
    renderTopBar();

    await userEvent.click(screen.getByRole("button", { name: "External APIs" }));

    expect(await screen.findByText("No apps yet")).toBeInTheDocument();
  });

  it("puts the navigation menu first in the bar", () => {
    renderTopBar();

    expect(within(screen.getByRole("banner")).getAllByRole("button")[0]).toHaveAccessibleName("Menu");
  });

  it("opens the navigation over the page with the menu button", async () => {
    renderTopBar();

    await userEvent.click(screen.getByRole("button", { name: "Menu" }));

    expect(await screen.findByRole("dialog", { name: "Navigation" })).toBeInTheDocument();
  });

  it("links home and chat; settings live in the search bar and the account menu, not as a bar icon", () => {
    renderTopBar();

    expect(screen.getByRole("link", { name: "contor home" })).toHaveAttribute("href", "/");
    expect(screen.getByRole("link", { name: "Chat" })).toHaveAttribute("href", "/chat");
    expect(within(screen.getByRole("banner")).queryByRole("link", { name: "Settings" })).not.toBeInTheDocument();
  });

  it("is a compact white bar separated from the page by a hairline", () => {
    renderTopBar();

    expect(screen.getByRole("banner")).toHaveClass("h-(--header-height)", "bg-background", "border-b");
    expect(screen.getByRole("banner")).not.toHaveClass("shadow-(--shadow-card)");
  });

  it("has one search in the middle: the command palette's bar, no separate mail search", () => {
    renderTopBar();
    const banner = screen.getByRole("banner");

    expect(within(banner).queryByRole("searchbox", { name: "Search mail" })).not.toBeInTheDocument();
    const search = within(banner).getByTestId("search-bar");
    expect(within(search).getByRole("combobox", { name: "Search or jump to" })).toBeInTheDocument();
    expect(search.parentElement).toHaveClass("flex-1", "justify-center");
  });

  it("shows the account menu", async () => {
    renderTopBar();

    expect(await screen.findByRole("button", { name: /owner/ })).toBeInTheDocument();
  });
});
