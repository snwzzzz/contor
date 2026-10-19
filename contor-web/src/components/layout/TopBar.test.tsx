import { screen } from "@testing-library/react";
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
    });
  });

  it("opens the (still empty) app launcher", async () => {
    renderTopBar();

    await userEvent.click(screen.getByRole("button", { name: "Apps" }));

    expect(await screen.findByText("No apps yet")).toBeInTheDocument();
  });

  it("opens the navigation over the page with the menu button", async () => {
    renderTopBar();

    await userEvent.click(screen.getByRole("button", { name: "Menu" }));

    expect(await screen.findByRole("dialog", { name: "Navigation" })).toBeInTheDocument();
  });

  it("links home, chat and the settings of the current page", () => {
    renderTopBar();

    expect(screen.getByRole("link", { name: "contor home" })).toHaveAttribute("href", "/");
    expect(screen.getByRole("link", { name: "Chat" })).toHaveAttribute("href", "/chat");
    expect(screen.getByRole("link", { name: "Settings" })).toHaveAttribute("href", "/calendar?settings=accounts");
  });

  it("is a compact white bar separated from the page by a hairline", () => {
    renderTopBar();

    expect(screen.getByRole("banner")).toHaveClass("h-(--header-height)", "bg-background", "border-b");
    expect(screen.getByRole("banner")).not.toHaveClass("shadow-(--shadow-card)");
  });

  it("shows the account menu", async () => {
    renderTopBar();

    expect(await screen.findByRole("button", { name: /owner/ })).toBeInTheDocument();
  });
});
