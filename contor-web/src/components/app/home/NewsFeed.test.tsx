import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { NewsFeed, type NewsItem } from "./NewsFeed";

const items: NewsItem[] = [
  {
    id: "a",
    subject: "Invoice October",
    preview: "Please find attached the invoice for October.",
    people: ["Anna Blum", "Billing"],
    received: "09:12",
    unread: true,
  },
  { id: "b", subject: "Team lunch", preview: "Friday at noon?", people: ["Chris Ehring"], received: "Yesterday", unread: false },
];

describe("NewsFeed", () => {
  it("shows each item with its subject, time, people and preview", () => {
    render(<NewsFeed items={items} />);

    expect(screen.queryByRole("table")).not.toBeInTheDocument();
    const item = screen.getByRole("button", { name: "Invoice October" }).closest("li") as HTMLElement;
    expect(within(item).getByText("Please find attached the invoice for October.")).toBeInTheDocument();
    expect(within(item).getByText("09:12 · Anna Blum, Billing")).toBeInTheDocument();
  });

  it("shows new items in bold until opened", async () => {
    render(<NewsFeed items={items} />);

    const item = (subject: string) => screen.getByRole("button", { name: subject }).closest("li");
    expect(item("Invoice October")).toHaveAttribute("data-unread", "true");
    expect(item("Team lunch")).not.toHaveAttribute("data-unread");

    await userEvent.click(screen.getByRole("button", { name: "Invoice October" }));

    expect(item("Invoice October")).not.toHaveAttribute("data-unread");
  });

  it("says when there is nothing new", () => {
    render(<NewsFeed items={[]} />);

    expect(screen.getByText("Nothing new")).toBeInTheDocument();
  });
});
