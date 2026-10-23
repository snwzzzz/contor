import { screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { navigationState, resetNavigation } from "@/test/next-navigation";
import { renderWithProviders } from "@/test/render";
import { RecordLink } from "./RecordLink";

vi.mock("next/navigation", () => import("@/test/next-navigation"));

const nextLink = vi.hoisted(() => vi.fn());
vi.mock("next/link", () => ({
  default: (props: { href: string; replace?: boolean; scroll?: boolean; children: React.ReactNode }) => {
    nextLink(props);
    return <a href={props.href}>{props.children}</a>;
  },
}));

describe("RecordLink", () => {
  beforeEach(() => {
    resetNavigation("/calendar");
    nextLink.mockReset();
  });

  it("opens the record in the sidebar over the current page, adding one history entry", () => {
    navigationState.search = "view=month";
    renderWithProviders(<RecordLink collectionId={3} recordId={1}>Interview</RecordLink>);

    expect(screen.getByRole("link", { name: "Interview" })).toHaveAttribute("href", "/calendar?view=month&record=3-1");
    expect(nextLink).toHaveBeenLastCalledWith(expect.objectContaining({ replace: false, scroll: false }));
  });

  it("switches records in place while the sidebar is open, so Back closes it instead of stepping through", () => {
    navigationState.search = "record=3-1&recordTab=emails";
    renderWithProviders(<RecordLink collectionId={5} recordId={8}>Acme</RecordLink>);

    expect(screen.getByRole("link", { name: "Acme" })).toHaveAttribute("href", "/calendar?record=5-8");
    expect(nextLink).toHaveBeenLastCalledWith(expect.objectContaining({ replace: true }));
  });
});
