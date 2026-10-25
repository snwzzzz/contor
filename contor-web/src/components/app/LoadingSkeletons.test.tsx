import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { LoadingList, PageHeaderSkeleton, PlaceholderPageSkeleton } from "./LoadingSkeletons";

describe("LoadingSkeletons", () => {
  it("a list is a busy status named after what loads, one placeholder row each", () => {
    render(<LoadingList label="Loading emails" rows={4} avatar />);

    const status = screen.getByRole("status", { name: "Loading emails" });
    expect(status).toHaveAttribute("aria-busy", "true");
    expect(within(status).getAllByRole("listitem")).toHaveLength(4);
    expect(status.querySelectorAll(".rounded-full")).toHaveLength(4);
  });

  it("a page header is decoration: breadcrumbs, icon, title, the page's commands and Close", () => {
    const { container } = render(<PageHeaderSkeleton commands={3} />);

    const header = container.querySelector("[data-skeleton=page-header]");
    expect(header).toHaveAttribute("aria-hidden", "true");
    expect(header?.querySelector("[data-skeleton=trail]")).not.toBeNull();
    expect(header?.querySelector("[data-skeleton=page-icon]")).not.toBeNull();
    expect(header?.querySelectorAll("[data-skeleton=command]")).toHaveLength(3);
    expect(header?.querySelector("[data-skeleton=description]")).not.toBeNull();
  });

  it("a page header can leave out the icon and the description", () => {
    const { container } = render(<PageHeaderSkeleton commands={0} icon={false} description={false} />);

    expect(container.querySelector("[data-skeleton=page-icon]")).toBeNull();
    expect(container.querySelector("[data-skeleton=command]")).toBeNull();
    expect(container.querySelector("[data-skeleton=description]")).toBeNull();
  });

  it("a placeholder page is its header and the empty state's icon and two lines", () => {
    render(<PlaceholderPageSkeleton label="Loading chat" commands={2} />);

    const status = screen.getByRole("status", { name: "Loading chat" });
    expect(status).toHaveAttribute("aria-busy", "true");
    expect(status.querySelectorAll("[data-skeleton=command]")).toHaveLength(2);
    expect(status.querySelector("[data-skeleton=empty-state]")).not.toBeNull();
  });
});
