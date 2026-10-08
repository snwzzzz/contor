import { screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { apiError, mockApi } from "@/test/api-mock";
import { resetNavigation, router } from "@/test/next-navigation";
import { renderWithProviders } from "@/test/render";
import { DevAutoLogin } from "./DevAutoLogin";

vi.mock("next/navigation", () => import("@/test/next-navigation"));

const dev = { id: 1, username: "snwz", email: "snwz@outlook.com", role: "OWNER", totpEnabled: false };

describe("DevAutoLogin", () => {
  beforeEach(() => {
    resetNavigation("/login");
    document.cookie = "XSRF-TOKEN=t; path=/";
  });

  it("signs in as the dev account by itself and continues to next", async () => {
    const calls = mockApi({ "POST /api/auth/dev-login": { body: dev } });

    renderWithProviders(<DevAutoLogin username="snwz" next="/proposals" />);

    expect(screen.getByText(/signing in as snwz/i)).toBeInTheDocument();
    await waitFor(() => expect(router.replace).toHaveBeenCalledWith("/proposals"));
    expect(calls.filter((call) => call.method === "POST" && call.path === "/api/auth/dev-login")).toHaveLength(1);
  });

  it("falls back to the login form when dev sign-in fails", async () => {
    mockApi({ "POST /api/auth/dev-login": apiError(403, "Dev sign-in is only available on this machine") });

    renderWithProviders(<DevAutoLogin username="snwz" />);

    expect(await screen.findByRole("alert")).toHaveTextContent("Dev sign-in is only available on this machine");
    expect(screen.getByLabelText("Username")).toBeInTheDocument();
    expect(router.replace).not.toHaveBeenCalled();
  });
});
