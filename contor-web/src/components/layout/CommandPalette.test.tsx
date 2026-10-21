import { act, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { mockApi } from "@/test/api-mock";
import { resetNavigation, router } from "@/test/next-navigation";
import { renderWithProviders } from "@/test/render";
import { openCommandPalette } from "@/lib/command-palette";
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

  it("opens its list under the bar with Ctrl+K and Cmd+K; Escape closes it", async () => {
    renderWithProviders(<CommandPalette />);
    expect(screen.queryByRole("listbox")).not.toBeInTheDocument();

    await userEvent.keyboard("{Control>}k{/Control}");
    expect(screen.getByRole("combobox", { name: "Search or jump to" })).toHaveFocus();
    expect(await screen.findByRole("listbox")).toBeInTheDocument();

    await userEvent.keyboard("{Escape}");
    expect(screen.queryByRole("listbox")).not.toBeInTheDocument();
    await userEvent.keyboard("{Meta>}k{/Meta}");
    expect(await screen.findByRole("listbox")).toBeInTheDocument();
  });

  afterEach(() => vi.restoreAllMocks());

  it("shows the shortcut the way the operating system names it", () => {
    vi.spyOn(window.navigator, "platform", "get").mockReturnValue("Win32");
    const { unmount } = renderWithProviders(<CommandPalette />);
    expect(screen.getByText("Ctrl K")).toBeInTheDocument();
    unmount();

    vi.spyOn(window.navigator, "platform", "get").mockReturnValue("MacIntel");
    renderWithProviders(<CommandPalette />);
    expect(screen.getByText("⌘K")).toBeInTheDocument();
  });

  it("is the big search bar itself: the list drops down right underneath it (no dialog)", async () => {
    renderWithProviders(<CommandPalette />);
    const input = screen.getByRole("combobox", { name: "Search or jump to" });
    const bar = screen.getByTestId("search-bar");
    expect(bar).toHaveClass("w-full", "max-w-xl");
    expect(bar).toContainElement(input);

    await userEvent.click(input);

    const list = await screen.findByRole("listbox");
    expect(bar).toContainElement(list);
    expect(list.closest("[data-slot=search-dropdown]")).toHaveClass("absolute", "top-full");
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("closes when focus leaves it", async () => {
    renderWithProviders(
      <>
        <CommandPalette />
        <button type="button">Elsewhere</button>
      </>,
    );
    await userEvent.click(screen.getByRole("combobox", { name: "Search or jump to" }));
    await screen.findByRole("listbox");

    await userEvent.click(screen.getByRole("button", { name: "Elsewhere" }));

    expect(screen.queryByRole("listbox")).not.toBeInTheDocument();
  });

  it("opens when another part of the app asks for it", async () => {
    renderWithProviders(<CommandPalette />);

    act(() => openCommandPalette());

    expect(await screen.findByRole("listbox")).toBeInTheDocument();
    expect(screen.getByRole("combobox", { name: "Search or jump to" })).toHaveFocus();
  });

  it("searches the mail for what was typed (the inbox shows the results)", async () => {
    renderWithProviders(<CommandPalette />);

    await userEvent.type(screen.getByRole("combobox", { name: "Search or jump to" }), "invoice ACME");
    await userEvent.click(await screen.findByRole("option", { name: /Search mail for “invoice ACME”/ }));

    expect(router.push).toHaveBeenCalledWith("/inbox?q=invoice+ACME");
    expect(screen.queryByRole("listbox")).not.toBeInTheDocument();
    expect(screen.getByRole("combobox", { name: "Search or jump to" })).toHaveValue("");
  });

  it("offers the mail search only once something is typed", async () => {
    renderWithProviders(<CommandPalette />);
    await userEvent.click(screen.getByRole("combobox", { name: "Search or jump to" }));
    await screen.findByRole("option", { name: /Organizations/ });

    expect(screen.queryByRole("option", { name: /Search mail for/ })).not.toBeInTheDocument();
  });

  it("jumps to a collection by name", async () => {
    renderWithProviders(<CommandPalette />);
    await userEvent.keyboard("{Control>}k{/Control}");

    await userEvent.keyboard("organ");
    await screen.findByRole("option", { name: /Organizations/ });
    await userEvent.keyboard("{Enter}");

    expect(router.push).toHaveBeenCalledWith("/collections/2");
    expect(screen.queryByRole("listbox")).not.toBeInTheDocument();
  });

  it("opens the settings at General", async () => {
    renderWithProviders(<CommandPalette />);
    await userEvent.keyboard("{Control>}k{/Control}");

    await userEvent.click(await screen.findByRole("option", { name: /^Settings$/ }));

    expect(router.push).toHaveBeenCalledWith(expect.stringMatching(/\?settings=general$/));
  });

  it("has no theme actions (the theme is chosen in Settings › General)", async () => {
    renderWithProviders(<CommandPalette />);
    await userEvent.keyboard("{Control>}k{/Control}");
    await screen.findByRole("option", { name: /Proposals/ });

    expect(screen.queryByRole("option", { name: /theme/i })).not.toBeInTheDocument();
  });

  it("navigates to app sections", async () => {
    renderWithProviders(<CommandPalette />);
    await userEvent.keyboard("{Control>}k{/Control}");

    await userEvent.click(await screen.findByRole("option", { name: /Proposals/ }));

    expect(router.push).toHaveBeenCalledWith("/proposals");
  });
});
