import { screen, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { SidebarProvider } from "@/components/ui/sidebar";
import { mockApi } from "@/test/api-mock";
import { navigationState, resetNavigation } from "@/test/next-navigation";
import { renderWithProviders } from "@/test/render";
import { AppSidebar } from "./AppSidebar";

vi.mock("next/navigation", () => import("@/test/next-navigation"));

const collections = [
  { id: 2, slug: "organizations", name: "Organizations", system: true, matchKeys: [], displayConfig: {}, schemaVersion: 1, createdBy: "USER", fields: [] },
  { id: 1, slug: "people", name: "People", system: true, matchKeys: [], displayConfig: {}, schemaVersion: 1, createdBy: "USER", fields: [] },
];

function renderSidebar() {
  return renderWithProviders(
    <SidebarProvider>
      <AppSidebar />
    </SidebarProvider>,
  );
}

describe("AppSidebar", () => {
  beforeEach(() => {
    resetNavigation("/proposals");
    mockApi({
      "GET /api/collections": { body: collections },
      "GET /api/proposals/count": { body: { count: 3 } },
    });
  });

  it("links every section of the app", () => {
    renderSidebar();

    for (const [name, href] of [
      ["Inbox", "/inbox"],
      ["Proposals", "/proposals"],
      ["Calendar", "/calendar"],
      ["To-dos", "/tasks"],
      ["Relations", "/relations"],
      ["Queries", "/queries"],
      ["Rules & Labels", "/rules"],
      ["Settings", "/proposals?settings=accounts"],
      ["Accounts", "/proposals?settings=accounts"],
      ["AI Models", "/proposals?settings=ai"],
      ["Autonomy", "/proposals?settings=autonomy"],
      ["Security", "/proposals?settings=security"],
    ]) {
      expect(screen.getByRole("link", { name: new RegExp(`^${name}`) })).toHaveAttribute("href", href);
    }
  });

  it("lists the collections from the API and offers to create one", async () => {
    renderSidebar();

    expect(await screen.findByRole("link", { name: "People" })).toHaveAttribute("href", "/collections/1");
    expect(screen.getByRole("link", { name: "Organizations" })).toHaveAttribute("href", "/collections/2");
    expect(screen.getByRole("link", { name: /New collection/ })).toHaveAttribute("href", "/collections/new");
  });

  it("shows the number of pending proposals", async () => {
    renderSidebar();

    const proposals = screen.getByRole("link", { name: /^Proposals/ });
    expect(await within(proposals).findByLabelText("3 pending proposals")).toHaveTextContent("3");
  });

  it("marks the current page", () => {
    navigationState.pathname = "/proposals";
    renderSidebar();

    expect(screen.getByRole("link", { name: /^Proposals/ })).toHaveAttribute("aria-current", "page");
    expect(screen.getByRole("link", { name: /^Inbox/ })).not.toHaveAttribute("aria-current");
  });
});
