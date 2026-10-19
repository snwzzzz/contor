import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { ReadingPanePlaceholder } from "./ReadingPanePlaceholder";

describe("ReadingPanePlaceholder", () => {
  it("fills the reading pane as a framed, tinted area instead of floating text", () => {
    render(<ReadingPanePlaceholder />);

    const frame = screen.getByText("Select an email to read it").closest("[data-slot=reading-pane-placeholder]");
    expect(frame).toHaveClass("h-full", "rounded-lg", "border-dashed", "bg-muted/30");
    expect(screen.getByText("Pick one from the list to see the whole conversation.")).toBeInTheDocument();
  });
});
