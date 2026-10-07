import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { mockApi } from "@/test/api-mock";
import { resetNavigation, router } from "@/test/next-navigation";
import { renderWithProviders } from "@/test/render";
import { CommandPalette } from "./CommandPalette";

vi.mock("next/navigation", () => import("@/test/next-navigation"));
vi.mock("next-themes", () => ({ useTheme: () => ({ resolvedTheme: "light", setTheme: vi.fn() }) }));

describe("CommandPalette", () => {
  beforeEach(() => {
    resetNavigation();
    mockApi({
      "GET /api/collections": {
        body: [{ id: 2, slug: "organizations", name: "Organizations", system: true, matchKeys: [], displayConfig: {}, schemaVersion: 1, createdBy: "USER", fields: [] }],
      },
    });
  });

  it("opens with Ctrl+K and Cmd+K", async () => {
    renderWithProviders(<CommandPalette />);
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();

    await userEvent.keyboard("{Control>}k{/Control}");
    expect(await screen.findByRole("dialog")).toBeInTheDocument();

    await userEvent.keyboard("{Escape}");
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    await userEvent.keyboard("{Meta>}k{/Meta}");
    expect(await screen.findByRole("dialog")).toBeInTheDocument();
  });

  it("opens from the top bar button", async () => {
    renderWithProviders(<CommandPalette />);

    await userEvent.click(screen.getByRole("button", { name: /Search or jump to/ }));

    expect(await screen.findByRole("dialog")).toBeInTheDocument();
  });

  it("jumps to a collection by name", async () => {
    renderWithProviders(<CommandPalette />);
    await userEvent.keyboard("{Control>}k{/Control}");

    await userEvent.type(await screen.findByPlaceholderText("Search pages, collections and actions…"), "organ");
    await screen.findByRole("option", { name: /Organizations/ });
    await userEvent.keyboard("{Enter}");

    expect(router.push).toHaveBeenCalledWith("/collections/2");
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
  });

  it("navigates to app sections", async () => {
    renderWithProviders(<CommandPalette />);
    await userEvent.keyboard("{Control>}k{/Control}");

    await userEvent.click(await screen.findByRole("option", { name: /Proposals/ }));

    expect(router.push).toHaveBeenCalledWith("/proposals");
  });
});
