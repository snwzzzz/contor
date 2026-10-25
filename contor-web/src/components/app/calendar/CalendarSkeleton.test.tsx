import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { CalendarSkeleton } from "./CalendarSkeleton";

describe("CalendarSkeleton", () => {
  it("is the calendar's shape: the toolbar with the view switch, seven day columns and the agenda", () => {
    render(<CalendarSkeleton />);

    const status = screen.getByRole("status", { name: "Loading calendar" });
    expect(status).toHaveAttribute("aria-busy", "true");
    expect(status.querySelector("[data-skeleton=view-switch]")).not.toBeNull();
    expect(status.querySelectorAll("[data-skeleton=day]")).toHaveLength(7);
    expect(status.querySelector("[data-skeleton=agenda]")).not.toBeNull();
  });
});
