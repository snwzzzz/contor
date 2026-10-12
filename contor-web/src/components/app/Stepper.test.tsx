import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { Stepper } from "./Stepper";

describe("Stepper", () => {
  it("shows every step with its state and marks the current one", () => {
    render(<Stepper label="Setup steps" steps={["Personal details", "ID proof", "Bank details"]} current={1} />);

    const list = screen.getByRole("list", { name: "Setup steps" });
    const items = within(list).getAllByRole("listitem");
    expect(items.map((item) => item.getAttribute("data-state"))).toEqual(["complete", "current", "upcoming"]);
    expect(items[1]).toHaveAttribute("aria-current", "step");
    expect(items[0]).toHaveTextContent("Personal details");
    expect(within(items[0]).getByText("completed")).toHaveClass("sr-only");
  });

  it("fills the bars of finished and current steps", () => {
    render(<Stepper label="Steps" steps={["One", "Two", "Three"]} current={2} />);

    const fills = screen.getAllByTestId("step-fill");
    expect(fills.map((fill) => fill.getAttribute("data-filled"))).toEqual(["true", "true", "true"]);
  });
});
