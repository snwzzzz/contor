import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { SearchField } from "./SearchField";

describe("SearchField", () => {
  it("is a labelled search box with a search icon in the shared field look", () => {
    render(<SearchField aria-label="Search collections" placeholder="Search…" value="" onChange={() => {}} />);

    const box = screen.getByRole("searchbox", { name: "Search collections" });
    expect(box).toHaveAttribute("data-slot", "input");
    expect(box).toHaveClass("pl-8");
    // Focus shows as a darker border only, no glow (ring/shadow) around the field.
    expect(box).toHaveClass("focus-visible:border-ring");
    expect(box.className).not.toMatch(/focus-visible:ring-(?!0)/);
    expect(box.parentElement?.querySelector("svg")).toHaveAttribute("aria-hidden", "true");
  });

  it("offers Clear only while there is text", async () => {
    const onClear = vi.fn();
    const { rerender } = render(<SearchField aria-label="Search" value="" onChange={() => {}} onClear={onClear} />);
    expect(screen.queryByRole("button", { name: "Clear search" })).not.toBeInTheDocument();

    rerender(<SearchField aria-label="Search" value="acme" onChange={() => {}} onClear={onClear} />);
    await userEvent.click(screen.getByRole("button", { name: "Clear search" }));

    expect(onClear).toHaveBeenCalledOnce();
  });

  it("works uncontrolled inside a form (name)", () => {
    render(
      <form role="search">
        <SearchField name="q" aria-label="Search mail" />
      </form>,
    );

    expect(screen.getByRole("searchbox", { name: "Search mail" })).toHaveAttribute("name", "q");
  });
});
