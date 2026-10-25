import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { HomeSkeleton } from "./HomeSkeleton";

describe("HomeSkeleton", () => {
  it("is the home page's shape: greeting, the four numbers, navigate and the five sections", () => {
    render(<HomeSkeleton />);

    const status = screen.getByRole("status", { name: "Loading home" });
    expect(status).toHaveAttribute("aria-busy", "true");
    expect(status.querySelector("[data-skeleton=greeting]")).not.toBeNull();
    expect(status.querySelectorAll("[data-skeleton=kpi]")).toHaveLength(4);
    expect(status.querySelectorAll("[data-skeleton=destination]")).toHaveLength(6);
    expect(status.querySelectorAll("[data-skeleton=section]")).toHaveLength(5);
  });
});
