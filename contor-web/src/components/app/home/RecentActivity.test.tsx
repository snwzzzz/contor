import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { mockApi } from "@/test/api-mock";
import { renderWithProviders } from "@/test/render";
import { RecentActivity } from "./RecentActivity";
import { email, NOW, page } from "./test-fixtures";

const recent = [
  email(1, "Invoice October", { read: false, labels: [{ labelId: 1, slug: "orders", name: "Orders", color: "#00ff00", source: "AI" }] }),
  email(2, "Team lunch", { fromName: "Chris Ehring", receivedAt: "2026-10-18T10:00:00Z" }),
];

function api(needsReply = [email(3, "Contract question", { fromName: "Legal" })]) {
  return mockApi({
    "GET /api/emails": ({ url }) => {
      if (url.searchParams.get("read") === "false") return { body: page([recent[0]]) };
      if (url.searchParams.get("answered") === "false") return { body: page(needsReply) };
      return { body: page(recent) };
    },
    "GET /api/senders/logos": { body: [] },
    "GET /api/senders/contact-photos": { body: [] },
  });
}

describe("RecentActivity", () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(NOW);
  });
  afterEach(() => vi.useRealTimers());

  it("lists the latest emails with sender, snippet, label and time; unread ones stand out", async () => {
    api();
    renderWithProviders(<RecentActivity />);

    const invoice = await screen.findByRole("link", { name: /Invoice October/ });
    expect(invoice).toHaveAttribute("href", "/inbox?email=1");
    expect(invoice).toHaveAttribute("data-unread", "true");
    expect(within(invoice).getByText("Anna Blum")).toBeInTheDocument();
    expect(within(invoice).getByText("About Invoice October")).toBeInTheDocument();
    expect(within(invoice).getByText("Orders")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Team lunch/ })).not.toHaveAttribute("data-unread");
    expect(screen.getByRole("link", { name: "Open mailbox" })).toHaveAttribute("href", "/inbox");
  });

  it("switches between recent, unread and emails that need a reply", async () => {
    api();
    renderWithProviders(<RecentActivity />);
    await screen.findByRole("link", { name: /Team lunch/ });

    await userEvent.click(screen.getByRole("tab", { name: "Unread" }));
    expect(await screen.findByRole("link", { name: /Invoice October/ })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /Team lunch/ })).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("tab", { name: "Needs reply" }));
    expect(await screen.findByRole("link", { name: /Contract question/ })).toBeInTheDocument();
  });

  it("pages through more emails than fit", async () => {
    mockApi({
      "GET /api/emails": { body: page([1, 2, 3, 4, 5, 6, 7].map((id) => email(id, `Mail ${id}`))) },
      "GET /api/senders/logos": { body: [] },
      "GET /api/senders/contact-photos": { body: [] },
    });
    renderWithProviders(<RecentActivity />);

    expect(await screen.findByRole("link", { name: /Mail 6/ })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /Mail 7/ })).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Next page" }));

    expect(screen.getByRole("link", { name: /Mail 7/ })).toBeInTheDocument();
  });

  it("explains an empty tab and points to the mailbox", async () => {
    api([]);
    renderWithProviders(<RecentActivity />);

    await userEvent.click(screen.getByRole("tab", { name: "Needs reply" }));

    expect(await screen.findByText("No emails waiting for a reply")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Go to inbox" })).toHaveAttribute("href", "/inbox");
  });
});
