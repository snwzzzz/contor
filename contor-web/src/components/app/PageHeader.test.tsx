import { render, screen, within } from "@testing-library/react";
import { CalendarDays } from "lucide-react";
import { describe, expect, it } from "vitest";
import { collectionTrail, PageHeader, PageTrail } from "./PageHeader";

describe("PageHeader", () => {
  it("renders the page title, description and actions", () => {
    render(<PageHeader title="Proposals" description="What the AI suggests" actions={<button>Refresh</button>} />);

    expect(screen.getByRole("heading", { level: 1, name: "Proposals" })).toBeInTheDocument();
    expect(screen.getByText("What the AI suggests")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Refresh" })).toBeInTheDocument();
  });

  it("leads from Home to the page and closes back to the level above", () => {
    render(<PageHeader title="Calendar" />);

    const trail = screen.getByRole("navigation", { name: "Breadcrumb" });
    expect(within(trail).getByRole("link", { name: "Home" })).toHaveAttribute("href", "/");
    expect(within(trail).getByText("Calendar")).toHaveAttribute("aria-current", "page");
    expect(screen.getByRole("link", { name: "Close" })).toHaveAttribute("href", "/");
  });

  it("puts the page commands between the title and Close, on the title row", () => {
    render(<PageHeader title="Calendar" actions={<button>Refresh</button>} />);

    const heading = screen.getByRole("heading", { level: 1 });
    const command = screen.getByRole("button", { name: "Refresh" });
    const close = screen.getByRole("link", { name: "Close" });
    expect(heading.compareDocumentPosition(command) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(command.compareDocumentPosition(close) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(within(screen.getByRole("navigation", { name: "Breadcrumb" }).parentElement as HTMLElement).queryByRole("link", { name: "Close" })).toBeNull();
  });

  it("follows a deeper trail and closes to its last level", () => {
    render(<PageHeader title="Organizations" trail={[{ label: "Collections", href: "/collections" }]} />);

    const trail = screen.getByRole("navigation", { name: "Breadcrumb" });
    expect(within(trail).getAllByRole("link").map((link) => link.textContent)).toEqual(["Home", "Collections"]);
    expect(screen.getByRole("link", { name: "Close" })).toHaveAttribute("href", "/collections");
  });

  it("shows a short subtitle after the title, outside the heading", () => {
    render(<PageHeader title="Organizations" subtitle="Collection" />);

    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent(/^Organizations$/);
    expect(screen.getByText("Collection")).toBeInTheDocument();
  });

  it("shows the section icon bare, in its color, without a tinted box", () => {
    const { container } = render(<PageHeader title="Calendar" icon={CalendarDays} tint={1} />);

    const icon = container.querySelector("[data-slot=page-icon]");
    expect(icon).toHaveClass("text-tint-1-vivid");
    expect(icon).not.toHaveClass("bg-tint-1");
    expect(icon).toHaveAttribute("aria-hidden", "true");
  });

  it("shows a picture instead of the icon (records)", () => {
    render(<PageHeader title="Anna Berger" media={<span data-testid="avatar">A</span>} />);

    expect(screen.getByRole("heading", { level: 1 }).closest("header")).toContainElement(screen.getByTestId("avatar"));
  });

  it("has no icon without an icon", () => {
    const { container } = render(<PageHeader title="Inbox" />);

    expect(container.querySelector("[data-slot=page-icon]")).toBeNull();
  });
});

describe("PageTrail", () => {
  it("serves pages with their own header (records, reports)", () => {
    render(
      <PageTrail
        trail={[
          { label: "Collections", href: "/collections" },
          { label: "People", href: "/collections/1" },
        ]}
        current="Anna Berger"
      />,
    );

    const trail = screen.getByRole("navigation", { name: "Breadcrumb" });
    expect(within(trail).getAllByRole("link").map((link) => link.textContent)).toEqual(["Home", "Collections", "People"]);
    expect(screen.getByRole("link", { name: "Close" })).toHaveAttribute("href", "/collections/1");
  });
});

describe("collectionTrail", () => {
  it("leads through the collections overview to the collection", () => {
    expect(collectionTrail({ id: 1, slug: "people", name: "People" })).toEqual([
      { label: "Collections", href: "/collections" },
      { label: "People", href: "/collections/1" },
    ]);
  });

  it("leads through the calendar and to-do pages for their system collections", () => {
    expect(collectionTrail({ id: 3, slug: "events", name: "Calendar" })).toEqual([{ label: "Calendar", href: "/calendar" }]);
    expect(collectionTrail({ id: 4, slug: "tasks", name: "To-dos" })).toEqual([{ label: "To-dos", href: "/tasks" }]);
  });

  it("names a collection that is still loading neutrally", () => {
    expect(collectionTrail({ id: 7 })).toEqual([
      { label: "Collections", href: "/collections" },
      { label: "Collection", href: "/collections/7" },
    ]);
  });
});
