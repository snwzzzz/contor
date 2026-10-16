import { screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { mockApi } from "@/test/api-mock";
import { resetNavigation } from "@/test/next-navigation";
import { renderWithProviders } from "@/test/render";
import { HomeMailboxes } from "./HomeMailboxes";

vi.mock("next/navigation", () => import("@/test/next-navigation"));

describe("HomeMailboxes", () => {
  beforeEach(() => resetNavigation("/"));

  it("lists the connected mailboxes as links and offers to manage them", async () => {
    mockApi({
      "GET /api/mail/accounts": {
        body: [
          { id: 1, provider: "MICROSOFT", emailAddress: "me@outlook.com", displayName: "Me", status: "ACTIVE" },
          { id: 2, provider: "MICROSOFT", emailAddress: "work@example.com", status: "NEEDS_REAUTH" },
        ],
      },
    });
    renderWithProviders(<HomeMailboxes />);

    expect(await screen.findByRole("link", { name: "me@outlook.com" })).toHaveAttribute("href", "/inbox");
    expect(screen.getByRole("link", { name: "work@example.com" })).toHaveAttribute("href", "/inbox");
    expect(screen.getByText("Needs sign-in")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Manage" })).toHaveAttribute("href", "/?settings=accounts");
  });

  it("offers to connect one when there is none", async () => {
    mockApi({ "GET /api/mail/accounts": { body: [] } });
    renderWithProviders(<HomeMailboxes />);

    expect(await screen.findByRole("link", { name: "Connect a mailbox" })).toHaveAttribute("href", "/?settings=accounts");
  });
});
