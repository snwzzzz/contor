import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { hardNavigate } from "@/lib/browser";
import { mockApi } from "@/test/api-mock";
import { resetNavigation } from "@/test/next-navigation";
import { renderWithProviders } from "@/test/render";
import { AccountOverview } from "./AccountOverview";

vi.mock("next/navigation", () => import("@/test/next-navigation"));
vi.mock("@/lib/browser", () => ({ hardNavigate: vi.fn() }));

const me = { id: 1, username: "anna", email: "anna@example.com", role: "OWNER", totpEnabled: false };

describe("AccountOverview", () => {
  beforeEach(() => {
    resetNavigation("/inbox");
    document.cookie = "XSRF-TOKEN=t; path=/";
  });

  it("shows who is signed in: username, email address and role, as plain rows", async () => {
    mockApi({ "GET /api/auth/me": { body: me } });
    renderWithProviders(<AccountOverview />);

    expect(await screen.findByText("anna@example.com")).toBeInTheDocument();
    expect(screen.getByText("anna")).toBeInTheDocument();
    expect(screen.getByText("Owner")).toBeInTheDocument();
    const row = screen.getByText("anna@example.com").closest("[data-slot=settings-row]");
    expect(row).not.toBeNull();
    expect(row?.className).not.toMatch(/\bborder\b(?!-)|rounded|shadow/);
    // The rows use the section's whole width, like the other settings sections.
    expect(row?.parentElement?.parentElement?.className).not.toMatch(/max-w-/);
  });

  it("logs out with a full page load", async () => {
    const calls = mockApi({ "GET /api/auth/me": { body: me }, "POST /api/auth/logout": { status: 204 } });
    renderWithProviders(<AccountOverview />);

    await userEvent.click(await screen.findByRole("button", { name: "Log out" }));

    await waitFor(() => expect(hardNavigate).toHaveBeenCalledWith("/login"));
    expect(calls.some((call) => call.method === "POST" && call.path === "/api/auth/logout")).toBe(true);
  });
});
