import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { LoadingCards, LoadingDetail, LoadingList, LoadingTable } from "./LoadingSkeletons";

describe("LoadingSkeletons", () => {
  it("a list is a busy status named after what loads, one placeholder row each", () => {
    render(<LoadingList label="Loading emails" rows={4} avatar />);

    const status = screen.getByRole("status", { name: "Loading emails" });
    expect(status).toHaveAttribute("aria-busy", "true");
    expect(within(status).getAllByRole("listitem")).toHaveLength(4);
    expect(status.querySelectorAll(".rounded-full")).toHaveLength(4);
  });

  it("a table has a header and the given rows and columns", () => {
    render(<LoadingTable label="Loading records" rows={3} columns={5} />);

    const status = screen.getByRole("status", { name: "Loading records" });
    // Header + rows.
    expect(status.querySelectorAll("[data-skeleton-row]")).toHaveLength(4);
    expect(status.querySelector("[data-skeleton-row]")?.children).toHaveLength(5);
  });

  it("a detail page and a card grid are busy statuses too", () => {
    render(
      <>
        <LoadingDetail label="Loading record" />
        <LoadingCards label="Loading proposals" count={3} />
      </>,
    );

    expect(screen.getByRole("status", { name: "Loading record" })).toBeInTheDocument();
    expect(within(screen.getByRole("status", { name: "Loading proposals" })).getAllByRole("listitem")).toHaveLength(3);
  });
});
