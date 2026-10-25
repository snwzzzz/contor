import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { mockApi } from "@/test/api-mock";
import { followNavigation, navigationState, resetNavigation, router } from "@/test/next-navigation";
import { renderWithProviders } from "@/test/render";
import { RecordPage } from "./RecordPage";

vi.mock("next/navigation", () => import("@/test/next-navigation"));

const people = { id: 5, slug: "people", name: "People", system: false, matchKeys: [], displayConfig: {}, schemaVersion: 1, createdBy: "USER", fields: [] };
const anna = { id: 12, collectionId: 5, title: "Anna Berg", values: {}, extra: {}, createdAt: "2026-10-01T00:00:00Z", createdBy: "AI" };

describe("RecordPage", () => {
  beforeEach(() => {
    resetNavigation("/collections/5/records/12");
    followNavigation();
    mockApi({
      "GET /api/collections": { body: [people] },
      "GET /api/collections/5": { body: people },
      "GET /api/collections/5/records/12": { body: anna },
      "GET /api/collections/5/records/12/emails": { body: { items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 } },
      "GET /api/collections/5/records/12/reports": { body: [] },
      "GET /api/relations": { body: [] },
      "GET /api/relation-types": { body: [] },
      "GET /api/senders/contact-photos": { body: [] },
      "GET /api/senders/logos": { body: [] },
    });
  });

  it("opens at the tab named in the address (e.g. from the sidebar's full screen)", async () => {
    navigationState.search = "tab=connections";
    renderWithProviders(<RecordPage collectionId={5} recordId={12} />);

    expect(await screen.findByRole("tab", { name: "Connections" })).toHaveAttribute("aria-selected", "true");
  });

  it("keeps the chosen tab in the address, Details without one", async () => {
    renderWithProviders(<RecordPage collectionId={5} recordId={12} />);
    expect(await screen.findByRole("tab", { name: "Details" })).toHaveAttribute("aria-selected", "true");

    await userEvent.click(screen.getByRole("tab", { name: "Reports" }));
    expect(router.replace).toHaveBeenLastCalledWith("/collections/5/records/12?tab=reports", { scroll: false });
    expect(screen.getByRole("tab", { name: "Reports" })).toHaveAttribute("aria-selected", "true");

    await userEvent.click(screen.getByRole("tab", { name: "Details" }));
    expect(router.replace).toHaveBeenLastCalledWith("/collections/5/records/12", { scroll: false });
  });
});
