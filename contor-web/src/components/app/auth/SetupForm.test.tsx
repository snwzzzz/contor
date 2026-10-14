import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { apiError, mockApi } from "@/test/api-mock";
import { resetNavigation, router } from "@/test/next-navigation";
import { renderWithProviders } from "@/test/render";
import { SetupForm } from "./SetupForm";

vi.mock("next/navigation", () => import("@/test/next-navigation"));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

async function fill({ password = "Correct-Horse-7-Battery", confirm = password }: { password?: string; confirm?: string } = {}) {
  await userEvent.type(screen.getByLabelText("Setup token"), "token-from-log");
  await userEvent.type(screen.getByLabelText("Username"), "owner");
  await userEvent.type(screen.getByLabelText("Email"), "owner@outlook.com");
  await userEvent.type(screen.getByLabelText("Password"), password);
  await userEvent.type(screen.getByLabelText("Confirm password"), confirm);
  await userEvent.click(screen.getByRole("button", { name: "Create owner account" }));
}

describe("SetupForm", () => {
  beforeEach(() => {
    resetNavigation("/setup");
    document.cookie = "XSRF-TOKEN=t; path=/";
  });

  it("creates the owner and continues to the login", async () => {
    const calls = mockApi({
      "POST /api/setup": { status: 201, body: { id: 1, username: "owner", email: "owner@outlook.com", role: "OWNER", totpEnabled: false } },
    });
    renderWithProviders(<SetupForm />);

    await fill();

    await waitFor(() => expect(router.replace).toHaveBeenCalledWith("/login"));
    expect(calls[0].body).toEqual({
      token: "token-from-log",
      username: "owner",
      email: "owner@outlook.com",
      password: "Correct-Horse-7-Battery",
    });
  });

  it("does not send mismatching passwords", async () => {
    const calls = mockApi({});
    renderWithProviders(<SetupForm />);

    await fill({ confirm: "Something-Else-1" });

    expect(screen.getByLabelText("Confirm password")).toHaveAccessibleDescription("Passwords do not match");
    expect(calls).toHaveLength(0);
  });

  it("lists every password rule the server rejected", async () => {
    mockApi({ "POST /api/setup": apiError(400, "Password must contain a digit; Password is too common") });
    renderWithProviders(<SetupForm />);

    await fill();

    expect(await screen.findAllByRole("listitem")).toHaveLength(2);
    expect(router.replace).not.toHaveBeenCalled();
  });

  it("explains a wrong token", async () => {
    mockApi({ "POST /api/setup": apiError(403, "Invalid setup token") });
    renderWithProviders(<SetupForm />);

    await fill();

    expect(await screen.findByRole("alert")).toHaveTextContent("Invalid setup token");
  });
});
