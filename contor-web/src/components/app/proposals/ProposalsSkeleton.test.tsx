import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { ProposalsSkeleton } from "./ProposalsSkeleton";

describe("ProposalsSkeleton", () => {
  it("is the two filter rows over a grid of proposal cards with Reject/Approve", () => {
    render(<ProposalsSkeleton />);

    const status = screen.getByRole("status", { name: "Loading proposals" });
    expect(status).toHaveAttribute("aria-busy", "true");
    expect(status.querySelectorAll("[data-skeleton=toolbar]")).toHaveLength(2);
    const cards = status.querySelectorAll("[data-skeleton=card]");
    expect(cards).toHaveLength(6);
    expect(cards[0].querySelector("[data-skeleton=kind]")).not.toBeNull();
    expect(cards[0].querySelectorAll("[data-skeleton=decision]")).toHaveLength(2);
  });
});
