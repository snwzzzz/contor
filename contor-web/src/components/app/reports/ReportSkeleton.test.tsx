import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { ReportSkeleton } from "./ReportSkeleton";

describe("ReportSkeleton", () => {
  it("is breadcrumbs, the report header, the question and the answer next to the sources", () => {
    render(<ReportSkeleton />);

    const status = screen.getByRole("status", { name: "Loading report" });
    expect(status).toHaveAttribute("aria-busy", "true");
    expect(status.querySelector("[data-skeleton=trail]")).not.toBeNull();
    expect(status.querySelector("[data-skeleton=report-header]")).not.toBeNull();
    expect(status.querySelector("[data-skeleton=question]")).not.toBeNull();
    expect(status.querySelectorAll("[data-skeleton=answer-section]").length).toBeGreaterThan(1);
    expect(status.querySelectorAll("[data-skeleton=source]").length).toBeGreaterThan(1);
  });

  it("inside the record's Reports tab it has no breadcrumbs", () => {
    render(<ReportSkeleton embedded />);

    expect(screen.getByRole("status", { name: "Loading report" }).querySelector("[data-skeleton=trail]")).toBeNull();
  });
});
