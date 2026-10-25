import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { CollectionTableSkeleton, CollectionsOverviewSkeleton, RecordSkeleton } from "./CollectionSkeletons";

describe("CollectionSkeletons", () => {
  it("the overview is search and origin chips over the table of collections", () => {
    render(<CollectionsOverviewSkeleton />);

    const status = screen.getByRole("status", { name: "Loading collections" });
    expect(status).toHaveAttribute("aria-busy", "true");
    expect(status.querySelector("[data-skeleton=search]")).not.toBeNull();
    expect(status.querySelectorAll("[data-skeleton=chip]")).toHaveLength(4);
    expect(status.querySelector("[data-skeleton=table-head]")).not.toBeNull();
    expect(status.querySelectorAll("[data-skeleton=row]").length).toBeGreaterThan(3);
  });

  it("a collection is its page header, the table toolbar and a table of columns and rows", () => {
    render(<CollectionTableSkeleton rows={5} columns={4} />);

    const status = screen.getByRole("status", { name: "Loading collection" });
    expect(status.querySelector("[data-skeleton=page-header]")).not.toBeNull();
    expect(status.querySelector("[data-skeleton=search]")).not.toBeNull();
    expect(status.querySelector("[data-skeleton=table-head]")?.querySelectorAll("th")).toHaveLength(5);
    expect(status.querySelectorAll("[data-skeleton=row]")).toHaveLength(5);
  });

  it("a record page is breadcrumbs, the picture next to the title, quick actions, four tabs and property rows", () => {
    render(<RecordSkeleton />);

    const status = screen.getByRole("status", { name: "Loading record" });
    expect(status.querySelector("[data-skeleton=trail]")).not.toBeNull();
    expect(status.querySelector("[data-skeleton=picture]")).toHaveStyle({ width: "64px" });
    expect(status.querySelectorAll("[data-skeleton=quick-action]").length).toBeGreaterThan(0);
    expect(status.querySelectorAll("[data-skeleton=tab]")).toHaveLength(4);
    expect(status.querySelectorAll("[data-skeleton=property]").length).toBeGreaterThan(3);
  });

  it("a record in the sidebar has a smaller picture and no breadcrumbs", () => {
    render(<RecordSkeleton variant="sheet" />);

    const status = screen.getByRole("status", { name: "Loading record" });
    expect(status.querySelector("[data-skeleton=trail]")).toBeNull();
    expect(status.querySelector("[data-skeleton=picture]")).toHaveStyle({ width: "48px" });
  });
});
