import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { mockApi } from "@/test/api-mock";
import { navigationState, resetNavigation } from "@/test/next-navigation";
import { renderWithProviders } from "@/test/render";
import { NavDrawer } from "./NavDrawer";

vi.mock("next/navigation", () => import("@/test/next-navigation"));

const collections = [
  { id: 2, slug: "organizations", name: "Organizations", system: true, matchKeys: [], displayConfig: {}, schemaVersion: 1, createdBy: "USER", fields: [] },
  { id: 1, slug: "people", name: "People", system: true, matchKeys: [], displayConfig: {}, schemaVersion: 1, createdBy: "USER", fields: [] },
];

async function openDrawer() {
  renderWithProviders(
    <>
      <NavDrawer />
      <p>Page content</p>
    </>,
  );
  await userEvent.click(screen.getByRole("button", { name: "Menu" }));
  return screen.findByRole("dialog", { name: "Navigation" });
}

describe("NavDrawer", () => {
  beforeEach(() => {
    resetNavigation("/proposals");
    mockApi({
      "GET /api/collections": { body: collections },
      "GET /api/proposals/count": { body: { count: 3 } },
    });
  });

  afterEach(() => window.localStorage.clear());

  it("is closed until the menu button is pressed", () => {
    renderWithProviders(<NavDrawer />);

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("links every section of the app", async () => {
    const drawer = await openDrawer();

    for (const [name, href] of [
      ["Home", "/"],
      ["Inbox", "/inbox"],
      ["Calendar", "/calendar"],
      ["To-dos", "/tasks"],
      ["Proposals", "/proposals"],
      ["Findings", "/findings"],
      ["Relations", "/relations"],
      ["Queries", "/queries"],
      ["Rules & Labels", "/rules"],
    ]) {
      expect(within(drawer).getByRole("link", { name: new RegExp(`^${name}`) })).toHaveAttribute("href", href);
    }
    expect(within(drawer).getByRole("list", { name: "AI insights" })).toBeInTheDocument();
  });

  it("lists the collections from the API and offers to create one", async () => {
    const drawer = await openDrawer();

    expect(await within(drawer).findByRole("link", { name: "People" })).toHaveAttribute("href", "/collections/1");
    expect(within(drawer).getByRole("link", { name: "Organizations" })).toHaveAttribute("href", "/collections/2");
    expect(within(drawer).getByRole("link", { name: /New collection/ })).toHaveAttribute("href", "/collections/new");
  });

  it("filters the pages and collections by the search", async () => {
    const drawer = await openDrawer();
    await within(drawer).findByRole("link", { name: "People" });

    await userEvent.type(within(drawer).getByRole("searchbox", { name: "Search pages" }), "peo");

    expect(within(drawer).getAllByRole("link").map((link) => link.textContent)).toEqual(["People"]);

    await userEvent.clear(within(drawer).getByRole("searchbox", { name: "Search pages" }));
    await userEvent.type(within(drawer).getByRole("searchbox", { name: "Search pages" }), "xyz");

    expect(within(drawer).getByText("No pages found")).toBeInTheDocument();
  });

  it("closes when a page is chosen", async () => {
    const drawer = await openDrawer();

    await userEvent.click(within(drawer).getByRole("link", { name: /^Inbox/ }));

    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
  });

  it("closes with Escape", async () => {
    await openDrawer();

    await userEvent.keyboard("{Escape}");

    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
  });

  it("shows the number of pending proposals", async () => {
    const drawer = await openDrawer();

    const proposals = within(drawer).getByRole("link", { name: /^Proposals/ });
    expect(await within(proposals).findByLabelText("3 pending proposals")).toHaveTextContent("3");
  });

  it("marks the current page, home only on itself", async () => {
    navigationState.pathname = "/proposals";
    const drawer = await openDrawer();

    expect(within(drawer).getByRole("link", { name: /^Proposals/ })).toHaveAttribute("aria-current", "page");
    expect(within(drawer).getByRole("link", { name: /^Home/ })).not.toHaveAttribute("aria-current");
  });

  it("moves a collection with the keyboard and remembers the order", async () => {
    const drawer = await openDrawer();
    await within(drawer).findByRole("link", { name: "People" });

    within(drawer).getByRole("button", { name: "Move Organizations" }).focus();
    await userEvent.keyboard("{Alt>}{ArrowDown}{/Alt}");

    const list = within(drawer).getByRole("list", { name: "Collections" });
    expect(within(list).getAllByRole("link").map((link) => link.textContent)).toEqual(["People", "Organizations", "New collection"]);
    expect(JSON.parse(window.localStorage.getItem("contor.sidebar.collectionOrder") ?? "[]")).toEqual([1, 2]);
  });

  it("previews where a dragged collection will land and saves it on drop", async () => {
    const drawer = await openDrawer();
    const list = await within(drawer).findByRole("list", { name: "Collections" });
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
  });

  it("restores the saved collection order", async () => {
    window.localStorage.setItem("contor.sidebar.collectionOrder", "[1, 2]");
    const drawer = await openDrawer();

    const list = await within(drawer).findByRole("list", { name: "Collections" });
    await within(list).findByRole("link", { name: "People" });
    expect(within(list).getAllByRole("link").map((link) => link.textContent)).toEqual(["People", "Organizations", "New collection"]);
  });

  it("opens and closes with Ctrl+B", async () => {
    renderWithProviders(<NavDrawer />);

    await userEvent.keyboard("{Control>}b{/Control}");
    expect(await screen.findByRole("dialog", { name: "Navigation" })).toBeInTheDocument();
  });
});
