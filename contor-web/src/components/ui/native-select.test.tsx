import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { NativeSelect } from "./native-select";

describe("NativeSelect", () => {
  it("is a native select in the field look with its own chevron, sized by the wrapper", async () => {
    const onChange = vi.fn();
    const { container } = render(
      <NativeSelect aria-label="Size" className="w-44" defaultValue="s" onChange={onChange}>
        <option value="s">Small</option>
        <option value="l">Large</option>
      </NativeSelect>,
    );

    const select = screen.getByRole("combobox", { name: "Size" });
    expect(select).toHaveAttribute("data-slot", "native-select");
    expect(select).toHaveClass("w-full", "border-input");
    // globals.css sets `appearance` (none, or base-select for the styled list); a utility class would override it.
    expect(select).not.toHaveClass("appearance-none");
    expect(container.firstElementChild).toHaveClass("w-44");
    expect(container.querySelector("svg[aria-hidden='true']")).not.toBeNull();

    await userEvent.selectOptions(select, "l");
    expect(onChange).toHaveBeenCalled();
  });

  it("has a smaller variant for dense places like table filters", () => {
    render(
      <NativeSelect aria-label="Filter" size="sm">
        <option>Any</option>
      </NativeSelect>,
    );

    expect(screen.getByRole("combobox", { name: "Filter" })).toHaveClass("h-8", "text-xs");
  });

  it("has a borderless variant for toolbars", () => {
    render(
      <NativeSelect aria-label="Rows" variant="ghost">
        <option>25 rows</option>
      </NativeSelect>,
    );

    const select = screen.getByRole("combobox", { name: "Rows" });
    expect(select).not.toHaveClass("border");
    expect(select).toHaveClass("hover:bg-accent");
  });
});
