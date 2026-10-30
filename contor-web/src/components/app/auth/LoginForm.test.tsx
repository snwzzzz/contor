import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { apiError, mockApi } from "@/test/api-mock";
import { resetNavigation, router } from "@/test/next-navigation";
import { renderWithProviders } from "@/test/render";
import { getGetCurrentUserQueryKey } from "@/lib/api/generated/endpoints/auth/auth";
import { LoginForm } from "./LoginForm";

vi.mock("next/navigation", () => import("@/test/next-navigation"));

const owner = { id: 1, username: "owner", email: "owner@example.com", role: "OWNER", totpEnabled: false };

async function submit(username = "owner", password = "Correct-Horse-7-Battery") {
  await userEvent.type(screen.getByLabelText("Username"), username);
  await userEvent.type(screen.getByLabelText("Password"), password);
  await userEvent.click(screen.getByRole("button", { name: "Log in" }));
}

describe("LoginForm", () => {
  beforeEach(() => {
    resetNavigation("/login");
    document.cookie = "XSRF-TOKEN=t; path=/";
  });

  it("logs in, caches the user and continues to the requested page", async () => {
    const calls = mockApi({ "POST /api/auth/login": { body: owner } });
    const { queryClient } = renderWithProviders(<LoginForm next="/collections/3" />);

    await submit();

    await waitFor(() => expect(router.replace).toHaveBeenCalledWith("/collections/3"));
    expect(calls[0].body).toEqual({ username: "owner", password: "Correct-Horse-7-Battery" });
    expect(queryClient.getQueryData(getGetCurrentUserQueryKey())).toEqual(owner);
  });

  it("falls back to the home page for unsafe targets", async () => {
    mockApi({ "POST /api/auth/login": { body: owner } });
    renderWithProviders(<LoginForm next="https://evil.example" />);

    await submit();

    await waitFor(() => expect(router.replace).toHaveBeenCalledWith("/"));
  });

  it("shows the server message on bad credentials and stays on the page", async () => {
    mockApi({ "POST /api/auth/login": apiError(401, "Invalid username or password") });
    const onUnauthorized = vi.fn();
    renderWithProviders(<LoginForm />, { onUnauthorized });

    await submit("owner", "wrong");

    expect(await screen.findByRole("alert")).toHaveTextContent("Invalid username or password");
    expect(router.replace).not.toHaveBeenCalled();
    expect(onUnauthorized).not.toHaveBeenCalled();
    expect(screen.getByLabelText("Password")).toHaveValue("");
  });

  it("explains rate limiting", async () => {
    mockApi({ "POST /api/auth/login": apiError(429, "Too many login attempts. Try again later.") });
    renderWithProviders(<LoginForm />);

    await submit();

    expect(await screen.findByRole("alert")).toHaveTextContent("Too many login attempts");
  });

  it("requires both fields before sending", async () => {
    const calls = mockApi({});
    renderWithProviders(<LoginForm />);

    await userEvent.click(screen.getByRole("button", { name: "Log in" }));

    expect(calls).toHaveLength(0);
  });
});
