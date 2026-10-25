import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { CalendarDays } from "lucide-react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { mockApi } from "@/test/api-mock";
import { resetNavigation } from "@/test/next-navigation";
import { renderWithProviders } from "@/test/render";
import { CollectionTableCommand, RefreshCommand, SettingsCommand, SyncMailCommand } from "./PageCommandButtons";
import { CommandLink, PageCommands } from "./PageCommands";

vi.mock("next/navigation", () => import("@/test/next-navigation"));

describe("PageCommands", () => {
  beforeEach(() => resetNavigation("/calendar"));

  it("groups the commands of a page in a toolbar", () => {
    renderWithProviders(
      <PageCommands>
        <CommandLink href="/tasks" icon={CalendarDays} label="To-dos" />
      </PageCommands>,
    );

    expect(screen.getByRole("toolbar", { name: "Page commands" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "To-dos" })).toHaveAttribute("href", "/tasks");
  });

  it("refreshes everything on the page", async () => {
    const { queryClient } = renderWithProviders(<RefreshCommand />);
    const invalidate = vi.spyOn(queryClient, "invalidateQueries");

    await userEvent.click(screen.getByRole("button", { name: "Refresh" }));

    expect(invalidate).toHaveBeenCalled();
  });

  it("syncs every active mailbox", async () => {
    const calls = mockApi({
      "GET /api/mail/accounts": {
        body: [
          { id: 1, provider: "MICROSOFT_GRAPH", emailAddress: "a@example.com", status: "ACTIVE" },
          { id: 2, provider: "MICROSOFT_GRAPH", emailAddress: "b@example.com", status: "NEEDS_REAUTH" },
          { id: 3, provider: "MICROSOFT_GRAPH", emailAddress: "c@example.com", status: "ACTIVE" },
        ],
      },
      "POST /api/mail/accounts/1/sync": { status: 202 },
      "POST /api/mail/accounts/3/sync": { status: 202 },
    });
    renderWithProviders(<SyncMailCommand />);

    const button = screen.getByRole("button", { name: "Sync mail" });
    await waitFor(() => expect(button).toBeEnabled());
    await userEvent.click(button);

    await waitFor(() =>
      expect(calls.filter((call) => call.method === "POST").map((call) => call.path)).toEqual([
        "/api/mail/accounts/1/sync",
        "/api/mail/accounts/3/sync",
      ]),
    );
  });

  it("opens a settings section over the current page", () => {
    renderWithProviders(<SettingsCommand section="accounts" label="Mailboxes" />);

    expect(screen.getByRole("link", { name: "Mailboxes" })).toHaveAttribute("href", "/calendar?settings=accounts");
  });

  it("opens the table of a system collection", async () => {
    mockApi({
      "GET /api/collections": {
        body: [{ id: 4, slug: "events", name: "Calendar", system: true, matchKeys: [], displayConfig: {}, schemaVersion: 1, createdBy: "USER", fields: [] }],
      },
    });
    renderWithProviders(<CollectionTableCommand slug="events" />);

    expect(await screen.findByRole("link", { name: "Table view" })).toHaveAttribute("href", "/collections/4");
  });
});
