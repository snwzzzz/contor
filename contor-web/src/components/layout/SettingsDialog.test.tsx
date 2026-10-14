import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { mockApi } from "@/test/api-mock";
import { navigationState, resetNavigation, router } from "@/test/next-navigation";
import { renderWithProviders } from "@/test/render";
import { SettingsDialog } from "./SettingsDialog";

vi.mock("next/navigation", () => import("@/test/next-navigation"));

function openAt(search: string) {
  navigationState.search = search;
  renderWithProviders(<SettingsDialog />);
}

describe("SettingsDialog", () => {
  beforeEach(() => {
    resetNavigation("/inbox");
    mockApi({ "GET /api/mail/accounts": { body: [] } });
  });

  it("stays closed without the settings parameter", () => {
    openAt("");

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("opens the requested section over the current page", () => {
    openAt("settings=ai");

    const dialog = screen.getByRole("dialog", { name: "Settings" });
    expect(within(dialog).getByRole("heading", { name: "AI Models" })).toBeInTheDocument();
    expect(within(dialog).getByRole("link", { name: "AI Models" })).toHaveAttribute("aria-current", "page");
  });

  it("switches sections without leaving the page", () => {
    openAt("settings=ai");

    expect(screen.getByRole("link", { name: "Security" })).toHaveAttribute("href", "/inbox?settings=security");
  });

  it("closes back to the page underneath", async () => {
    openAt("settings=ai");

    await userEvent.click(screen.getByRole("button", { name: "Close" }));

    expect(router.replace).toHaveBeenCalledWith("/inbox", { scroll: false });
  });

  it("filters the section list", async () => {
    openAt("settings=accounts");

    await userEvent.type(screen.getByPlaceholderText("Search settings"), "secu");

    const nav = screen.getByRole("navigation", { name: "Settings sections" });
    expect(within(nav).getAllByRole("link").map((link) => link.textContent)).toEqual(["Security"]);
  });

  it("shows the result of the Microsoft sign-in in the accounts section", async () => {
    openAt("settings=accounts&mailError=Microsoft+sign-in+was+cancelled.");

    expect(await screen.findByRole("alert")).toHaveTextContent("Microsoft sign-in was cancelled.");
  });
});
