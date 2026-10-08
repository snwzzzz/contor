import { render, screen } from "@testing-library/react";
import { CalendarDays } from "lucide-react";
import { describe, expect, it } from "vitest";
import { PageHeader } from "./PageHeader";

describe("PageHeader", () => {
  it("renders the page title, description and actions", () => {
    render(<PageHeader title="Proposals" description="What the AI suggests" actions={<button>Refresh</button>} />);

    expect(screen.getByRole("heading", { level: 1, name: "Proposals" })).toBeInTheDocument();
    expect(screen.getByText("What the AI suggests")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Refresh" })).toBeInTheDocument();
  });

  it("shows the section icon in its tint", () => {
    const { container } = render(<PageHeader title="Calendar" icon={CalendarDays} tint={1} />);

    const icon = container.querySelector("[data-slot=page-icon]");
    expect(icon).toHaveClass("bg-tint-1", "text-tint-1-foreground");
    expect(icon).toHaveAttribute("aria-hidden", "true");
  });

  it("has no icon box without an icon", () => {
    const { container } = render(<PageHeader title="Inbox" />);

    expect(container.querySelector("[data-slot=page-icon]")).toBeNull();
  });
});
