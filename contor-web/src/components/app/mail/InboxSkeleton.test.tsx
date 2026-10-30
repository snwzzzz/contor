import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { EmailListSkeleton, InboxSkeleton, ThreadSkeleton } from "./InboxSkeleton";

describe("InboxSkeleton", () => {
  it("is the inbox's two panes: folder switch, search and filter over email boxes, and the reading pane", () => {
    render(<InboxSkeleton />);

    const status = screen.getByRole("status", { name: "Loading inbox" });
    expect(status).toHaveAttribute("aria-busy", "true");
    expect(status.querySelector("[data-skeleton=folder-switch]")).not.toBeNull();
    expect(status.querySelector("[data-skeleton=search]")).not.toBeNull();
    expect(status.querySelectorAll("[data-skeleton=email]").length).toBeGreaterThan(4);
    expect(status.querySelector("[data-skeleton=reading-pane]")).not.toBeNull();
  });

  it("the list alone is email boxes under a period heading, each with a round picture", () => {
    render(<EmailListSkeleton rows={5} />);

    const status = screen.getByRole("status", { name: "Loading emails" });
    expect(status.querySelector("[data-skeleton=period]")).not.toBeNull();
    const emails = status.querySelectorAll("[data-skeleton=email]");
    expect(emails).toHaveLength(5);
    expect(emails[0].querySelector(".rounded-full")).not.toBeNull();
    expect(emails[0]).toHaveClass("bg-surface-email");
  });

  it("a conversation is the subject and one box per message", () => {
    render(<ThreadSkeleton />);

    const status = screen.getByRole("status", { name: "Loading conversation" });
    expect(status.querySelector("[data-skeleton=subject]")).not.toBeNull();
    expect(status.querySelectorAll("[data-skeleton=message]")).toHaveLength(3);
  });

  it("a conversation whose first message is known shows only the messages still coming", () => {
    render(<ThreadSkeleton subject={false} messages={2} />);

    const status = screen.getByRole("status", { name: "Loading conversation" });
    expect(status.querySelector("[data-skeleton=subject]")).toBeNull();
    expect(status.querySelectorAll("[data-skeleton=message]")).toHaveLength(2);
  });
});
