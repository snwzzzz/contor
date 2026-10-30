import { render, screen } from "@testing-library/react";
import { Reply } from "lucide-react";
import { describe, expect, it } from "vitest";
import { KindLabel } from "./KindLabel";

describe("KindLabel", () => {
  it("is a small bare icon in its color next to the kind, no tile", () => {
    render(
      <KindLabel icon={Reply} tint={5}>
        Reply suggestion
      </KindLabel>,
    );

    const label = screen.getByText("Reply suggestion");
    const icon = label.querySelector("svg");
    expect(icon).toHaveClass("size-3.5");
    expect(icon?.getAttribute("class")).toMatch(/text-tint-5-vivid/);
    expect(label.className).not.toMatch(/bg-|rounded/);
  });

  it("uses a muted icon without a tint", () => {
    render(<KindLabel icon={Reply}>Reply</KindLabel>);

    expect(screen.getByText("Reply").querySelector("svg")).toHaveClass("text-muted-foreground");
  });
});
