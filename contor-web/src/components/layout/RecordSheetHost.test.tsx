import { act, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { mockApi } from "@/test/api-mock";
import { followNavigation, navigationState, resetNavigation, router } from "@/test/next-navigation";
import { renderWithProviders } from "@/test/render";
import { RecordSheetHost } from "./RecordSheetHost";

vi.mock("next/navigation", () => import("@/test/next-navigation"));

const people = {
  id: 5, slug: "people", name: "People", system: false, matchKeys: [], displayConfig: {}, schemaVersion: 1, createdBy: "USER",
  fields: [{ id: 1, slug: "company", name: "Company", type: "TEXT", options: {}, required: false, uniqueValue: false, position: 1 }],
};
const anna = { id: 12, collectionId: 5, title: "Anna Berg", values: { company: "Acme" }, extra: {}, createdAt: "2026-10-01T00:00:00Z", createdBy: "AI" };

function openAt(search: string) {
  navigationState.search = search;
  renderWithProviders(<RecordSheetHost />);
}

describe("RecordSheetHost", () => {
  beforeEach(() => {
    resetNavigation("/collections/5");
    followNavigation();
    mockApi({
      "GET /api/collections/5": { body: people },
      "GET /api/collections/5/records/12": { body: anna },
      "GET /api/collections/5/records/12/emails": { body: { items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 } },
      "GET /api/relations": { body: [] },
      "GET /api/collections/5/records/12/reports": { body: [] },
      "GET /api/senders/contact-photos": { body: [] },
      "GET /api/senders/logos": { body: [] },
    });
  });

  it("stays closed without a record in the address", () => {
    openAt("");

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("opens only once React has hydrated the page, so its data never changes the page while it hydrates", async () => {
    // Server HTML of the page that React has not hydrated yet: no React fiber on its nodes.
    const main = document.createElement("main");
    main.innerHTML = '<div role="status" aria-label="Loading collection"></div>';
    document.body.append(main);
    try {
      openAt("record=5-12");
      await new Promise((resolve) => setTimeout(resolve, 300));
      // The sidebar and the page share queries (the collection): data arriving for the sidebar first would make the
      // page render something else than the server's HTML.
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();

      main.replaceChildren();
      expect(await screen.findByRole("dialog", { name: "Anna Berg" })).toBeInTheDocument();
    } finally {
      main.remove();
    }
  });

  it("opens the record named in the address on its Details tab, over the current page", async () => {
    openAt("record=5-12");

    const sheet = await screen.findByRole("dialog", { name: "Anna Berg" });
    expect(within(sheet).getByText("People")).toBeInTheDocument();
    expect(await within(sheet).findByRole("tab", { name: "Details" })).toHaveAttribute("aria-selected", "true");
    expect(within(sheet).getByRole("link", { name: "Open full screen" })).toHaveAttribute("href", "/collections/5/records/12");
  });

  it("keeps the sidebar's tab in the address, and full screen opens the page at that tab", async () => {
    openAt("q=anna&record=5-12&recordTab=connections");

    const sheet = await screen.findByRole("dialog", { name: "Anna Berg" });
    expect(await within(sheet).findByRole("tab", { name: "Connections" })).toHaveAttribute("aria-selected", "true");
    expect(within(sheet).getByRole("link", { name: "Open full screen" })).toHaveAttribute("href", "/collections/5/records/12?tab=connections");

    await userEvent.click(within(sheet).getByRole("tab", { name: /Emails/ }));

    expect(router.replace).toHaveBeenLastCalledWith("/collections/5?q=anna&record=5-12&recordTab=emails", { scroll: false });
    expect(within(sheet).getByRole("tab", { name: /Emails/ })).toHaveAttribute("aria-selected", "true");
    expect(within(sheet).getByRole("link", { name: "Open full screen" })).toHaveAttribute("href", "/collections/5/records/12?tab=emails");
  });

  it("closes by dropping the record from the address, keeping the page's own parameters", async () => {
    openAt("q=anna&record=5-12&recordTab=emails");
    await screen.findByRole("dialog", { name: "Anna Berg" });

    await userEvent.keyboard("{Escape}");

    expect(router.replace).toHaveBeenLastCalledWith("/collections/5?q=anna", { scroll: false });
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
  });

  it("closes with Back when it was opened from this page, so the page is never in the history twice", async () => {
    openAt("q=anna");
    act(() => router.push("/collections/5?q=anna&record=5-12"));
    await screen.findByRole("dialog", { name: "Anna Berg" });

    await userEvent.keyboard("{Escape}");

    expect(router.back).toHaveBeenCalledTimes(1);
    expect(router.replace).not.toHaveBeenCalled();
  });

  it("ignores an address that names no record", () => {
    openAt("record=abc");

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });
});
