import { fireEvent, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { mockApi } from "@/test/api-mock";
import { navigationState, resetNavigation } from "@/test/next-navigation";
import { renderWithProviders } from "@/test/render";
import { AppSidebar } from "./AppSidebar";
import { ResizableSidebarProvider } from "./ResizableSidebarProvider";

vi.mock("next/navigation", () => import("@/test/next-navigation"));

const collections = [
  { id: 2, slug: "organizations", name: "Organizations", system: true, matchKeys: [], displayConfig: {}, schemaVersion: 1, createdBy: "USER", fields: [] },
  { id: 1, slug: "people", name: "People", system: true, matchKeys: [], displayConfig: {}, schemaVersion: 1, createdBy: "USER", fields: [] },
];

function renderSidebar() {
  return renderWithProviders(
    <ResizableSidebarProvider>
      <AppSidebar />
    </ResizableSidebarProvider>,
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
      ["Findings", "/findings"],
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

  afterEach(() => window.localStorage.clear());

  it("shows the logo as a home link that survives the collapsed sidebar", () => {
    renderSidebar();

    const home = screen.getByRole("link", { name: "contor home" });
    expect(home).toHaveAttribute("href", "/inbox");
    expect(within(home).getByText("contor")).toBeInTheDocument();
  });

  it("moves a collection with the keyboard and remembers the order", async () => {
    renderSidebar();
    await screen.findByRole("link", { name: "People" });

    screen.getByRole("button", { name: "Move Organizations" }).focus();
    await userEvent.keyboard("{Alt>}{ArrowDown}{/Alt}");

    const list = screen.getByRole("list", { name: "Collections" });
    expect(within(list).getAllByRole("link").map((link) => link.textContent)).toEqual(["People", "Organizations", "New collection"]);
    expect(JSON.parse(window.localStorage.getItem("contor.sidebar.collectionOrder") ?? "[]")).toEqual([1, 2]);
  });

  it("previews where a dragged collection will land and saves it on drop", async () => {
    renderSidebar();
    const list = await screen.findByRole("list", { name: "Collections" });
    await within(list).findByRole("link", { name: "People" });
    const item = (name: string) => within(list).getByRole("link", { name }).closest("li") as HTMLElement;
    const dataTransfer = { effectAllowed: "", dropEffect: "", setData: () => {} };

    fireEvent.dragStart(item("Organizations"), { dataTransfer });
    fireEvent.dragOver(item("People"), { dataTransfer });

    expect(within(list).getAllByRole("link").map((link) => link.textContent)).toEqual(["People", "Organizations", "New collection"]);
    expect(item("Organizations")).toHaveAttribute("data-drag-preview", "true");
    expect(window.localStorage.getItem("contor.sidebar.collectionOrder")).toBeNull();

    fireEvent.drop(item("Organizations"), { dataTransfer });
    fireEvent.dragEnd(item("Organizations"), { dataTransfer });

    expect(JSON.parse(window.localStorage.getItem("contor.sidebar.collectionOrder") ?? "[]")).toEqual([1, 2]);
    expect(item("Organizations")).not.toHaveAttribute("data-drag-preview");
  });

  it("restores the saved collection order", async () => {
    window.localStorage.setItem("contor.sidebar.collectionOrder", "[1, 2]");
    renderSidebar();

    const list = await screen.findByRole("list", { name: "Collections" });
    await within(list).findByRole("link", { name: "People" });
    expect(within(list).getAllByRole("link").map((link) => link.textContent)).toEqual(["People", "Organizations", "New collection"]);
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
