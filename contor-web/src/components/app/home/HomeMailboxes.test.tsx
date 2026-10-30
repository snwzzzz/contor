import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { mockApi } from "@/test/api-mock";
import { resetNavigation } from "@/test/next-navigation";
import { renderWithProviders } from "@/test/render";
import { HomeMailboxes } from "./HomeMailboxes";

vi.mock("next/navigation", () => import("@/test/next-navigation"));

describe("HomeMailboxes", () => {
  beforeEach(() => {
    resetNavigation("/");
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date("2026-10-19T12:00:00Z"));
  });
  afterEach(() => vi.useRealTimers());

  it("lists each mailbox with its sync state and offers to manage them", async () => {
    mockApi({
      "GET /api/mail/accounts": {
        body: [
          { id: 1, provider: "MICROSOFT_GRAPH", emailAddress: "me@outlook.com", displayName: "Me", status: "ACTIVE", lastSyncedAt: "2026-10-19T11:55:00Z" },
          { id: 2, provider: "MICROSOFT_GRAPH", emailAddress: "work@example.com", status: "NEEDS_REAUTH" },
        ],
      },
    });
    renderWithProviders(<HomeMailboxes />);

    const me = (await screen.findByRole("link", { name: "me@outlook.com" })).closest("li") as HTMLElement;
    expect(within(me).getByRole("link", { name: "me@outlook.com" })).toHaveAttribute("href", "/inbox");
    expect(within(me).getByText("Synced 5 min. ago")).toBeInTheDocument();
    const work = screen.getByRole("link", { name: "work@example.com" }).closest("li") as HTMLElement;
    expect(within(work).getByText("Needs sign-in")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Manage" })).toHaveAttribute("href", "/?settings=accounts");
  });

  it("names the provider of each mailbox", async () => {
    mockApi({
      "GET /api/mail/accounts": {
        body: [
          { id: 1, provider: "MICROSOFT_GRAPH", emailAddress: "me@outlook.com", status: "ACTIVE", lastSyncedAt: "2026-10-19T11:55:00Z" },
          { id: 2, provider: "GMAIL", emailAddress: "me@gmail.com", status: "ACTIVE", lastSyncedAt: "2026-10-19T11:55:00Z" },
        ],
      },
    });
    renderWithProviders(<HomeMailboxes />);

    const outlook = (await screen.findByRole("link", { name: "me@outlook.com" })).closest("li") as HTMLElement;
    expect(within(outlook).getByText("Outlook")).toBeInTheDocument();
    const gmail = screen.getByRole("link", { name: "me@gmail.com" }).closest("li") as HTMLElement;
    expect(within(gmail).getByText("Gmail")).toBeInTheDocument();
    expect(within(gmail).getByText("Synced 5 min. ago")).toBeInTheDocument();
  });

  it("pages through many mailboxes", async () => {
    mockApi({
      "GET /api/mail/accounts": {
        body: [1, 2, 3, 4].map((id) => ({ id, provider: "MICROSOFT_GRAPH", emailAddress: `box${id}@example.com`, status: "ACTIVE" })),
      },
    });
    renderWithProviders(<HomeMailboxes />);

    expect(await screen.findByRole("link", { name: "box3@example.com" })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "box4@example.com" })).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Next page" }));

    expect(screen.getByRole("link", { name: "box4@example.com" })).toBeInTheDocument();
  });

  it("offers to connect one when there is none", async () => {
    mockApi({ "GET /api/mail/accounts": { body: [] } });
    renderWithProviders(<HomeMailboxes />);

    expect(await screen.findByRole("link", { name: "Connect a mailbox" })).toHaveAttribute("href", "/?settings=accounts");
  });
});
