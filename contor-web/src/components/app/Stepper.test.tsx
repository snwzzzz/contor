import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
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

  it("lets finished steps be chosen when it gets onSelect", async () => {
    const onSelect = vi.fn();
    render(<Stepper label="Steps" steps={["One", "Two", "Three"]} current={1} onSelect={onSelect} />);

    const list = screen.getByRole("list", { name: "Steps" });
    expect(within(list).getAllByRole("button")).toHaveLength(1);
    await userEvent.click(within(list).getByRole("button", { name: /One/ }));

    expect(onSelect).toHaveBeenCalledWith(0);
  });

  it("keeps every bar the same element when jumping back several steps, so they all animate", () => {
    const onSelect = vi.fn();
    const { rerender } = render(<Stepper label="Steps" steps={["One", "Two", "Three", "Four"]} current={3} onSelect={onSelect} />);
    const before = screen.getAllByTestId("step-fill");

    rerender(<Stepper label="Steps" steps={["One", "Two", "Three", "Four"]} current={1} onSelect={onSelect} />);

    const after = screen.getAllByTestId("step-fill");
    after.forEach((fill, index) => expect(fill).toBe(before[index]));
    expect(after.map((fill) => fill.getAttribute("data-filled"))).toEqual(["true", "true", "false", "false"]);
  });

  it("empties the bars one after another when going back: the last one first, then inward to the target", () => {
    const steps = ["One", "Two", "Three", "Four", "Five"];
    const { rerender } = render(<Stepper label="Steps" steps={steps} current={4} />);

    // From the 5th step back to the 1st.
    rerender(<Stepper label="Steps" steps={steps} current={0} />);

    const fills = screen.getAllByTestId("step-fill");
    const duration = parseInt(fills[0].style.transitionDuration, 10);
    const delays = fills.map((fill) => parseInt(fill.style.transitionDelay, 10));
    // Bar 5 first, then 4, 3 and 2, each once the one before has finished; bar 1 stays filled.
    expect(delays.slice(1)).toEqual([3 * duration, 2 * duration, duration, 0]);
    expect(fills.map((fill) => fill.getAttribute("data-filled"))).toEqual(["true", "false", "false", "false", "false"]);
  });

  it("fills the bars one after another when going forward several steps", () => {
    const steps = ["One", "Two", "Three", "Four"];
    const { rerender } = render(<Stepper label="Steps" steps={steps} current={0} />);

    rerender(<Stepper label="Steps" steps={steps} current={2} />);

    const delays = screen.getAllByTestId("step-fill").map((fill) => parseInt(fill.style.transitionDelay, 10));
    expect(delays[1]).toBeLessThan(delays[2]);
  });

  it("is plain text without onSelect", () => {
    render(<Stepper label="Steps" steps={["One", "Two"]} current={1} />);

    expect(within(screen.getByRole("list", { name: "Steps" })).queryByRole("button")).not.toBeInTheDocument();
  });

  it("numbers the steps", () => {
    render(<Stepper label="Setup steps" steps={["Personal details", "ID proof", "Bank details"]} current={0} />);

    const items = within(screen.getByRole("list", { name: "Setup steps" })).getAllByRole("listitem");
    expect(items.map((item) => within(item).getByTestId("step-number").textContent)).toEqual(["1", "2", "3"]);
    expect(within(items[1]).getByTestId("step-number")).toHaveClass("tabular-nums");
  });

  it("fills the bars of finished and current steps", () => {
    render(<Stepper label="Steps" steps={["One", "Two", "Three"]} current={2} />);

    const fills = screen.getAllByTestId("step-fill");
    expect(fills.map((fill) => fill.getAttribute("data-filled"))).toEqual(["true", "true", "true"]);
  });
});
