import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { FindingsSkeleton } from "./FindingsSkeleton";

describe("FindingsSkeleton", () => {
  it("is the filter chips over a grid of finding cards with picture, values and Dismiss/Keep", () => {
    render(<FindingsSkeleton />);

    const status = screen.getByRole("status", { name: "Loading findings" });
    expect(status).toHaveAttribute("aria-busy", "true");
    expect(status.querySelectorAll("[data-skeleton=chip]").length).toBeGreaterThan(1);
    const cards = status.querySelectorAll("[data-skeleton=card]");
    expect(cards).toHaveLength(6);
    expect(cards[0].querySelector("[data-skeleton=picture]")).not.toBeNull();
    expect(cards[0].querySelectorAll("[data-skeleton=decision]")).toHaveLength(2);
  });
});
