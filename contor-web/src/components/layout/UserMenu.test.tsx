import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { mockApi } from "@/test/api-mock";
import { resetNavigation } from "@/test/next-navigation";
import { renderWithProviders } from "@/test/render";
import { hardNavigate } from "@/lib/browser";
import { UserMenu } from "./UserMenu";

vi.mock("next/navigation", () => import("@/test/next-navigation"));
vi.mock("next-themes", () => ({ useTheme: () => ({ theme: "system", setTheme: vi.fn() }) }));
vi.mock("@/lib/browser", () => ({ hardNavigate: vi.fn() }));

describe("UserMenu", () => {
  beforeEach(() => {
    resetNavigation();
    document.cookie = "XSRF-TOKEN=t; path=/";
  });

  it("shows the current user and logs out with a full page load", async () => {
    const calls = mockApi({
      "GET /api/auth/me": { body: { id: 1, username: "owner", email: "owner@example.com", role: "OWNER", totpEnabled: false } },
      "POST /api/auth/logout": { status: 204 },
    });
    const { queryClient } = renderWithProviders(<UserMenu />);
    const clear = vi.spyOn(queryClient, "clear");

    await userEvent.click(await screen.findByRole("button", { name: /owner/ }));
    await userEvent.click(await screen.findByRole("menuitem", { name: "Log out" }));

    await waitFor(() => expect(hardNavigate).toHaveBeenCalledWith("/login"));
    expect(calls.some((call) => call.method === "POST" && call.path === "/api/auth/logout")).toBe(true);
    expect(clear).toHaveBeenCalled();
  });

  it("opens the settings (General); the theme is chosen there, not here", async () => {
    mockApi({ "GET /api/auth/me": { body: { id: 1, username: "owner", email: "owner@example.com", role: "OWNER", totpEnabled: false } } });
    renderWithProviders(<UserMenu />);

    await userEvent.click(await screen.findByRole("button", { name: /owner/ }));

    expect(await screen.findByRole("menuitem", { name: "My account" })).toHaveAttribute("href", "/inbox?settings=account");
    expect(screen.getByRole("menuitem", { name: "Settings" })).toHaveAttribute("href", "/inbox?settings=general");
    expect(screen.queryByRole("menuitem", { name: /Dark|Light|System/ })).not.toBeInTheDocument();
  });
});
