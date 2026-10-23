import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { mockApi } from "@/test/api-mock";
import { renderWithProviders } from "@/test/render";
import { RecordSheet } from "./RecordSheet";

vi.mock("next/navigation", () => import("@/test/next-navigation"));

const events = {
  id: 3,
  slug: "events",
  name: "Calendar",
  system: true,
  matchKeys: [],
  displayConfig: {},
  schemaVersion: 1,
  createdBy: "USER",
  fields: [
    { id: 1, slug: "starts_at", name: "Starts", type: "DATETIME", options: {}, required: true, uniqueValue: false, position: 1 },
    { id: 2, slug: "location", name: "Location", type: "TEXT", options: {}, required: false, uniqueValue: false, position: 2 },
  ],
};

const interview = {
  id: 1,
  collectionId: 3,
  title: "Interview at Acme",
  values: { starts_at: "2026-10-09T10:00:00Z", location: "Acme HQ" },
  extra: {},
  createdAt: "2026-10-01T00:00:00Z",
  createdBy: "AI",
};

describe("RecordSheet", () => {
  it("shows the record's detail page in the sidebar, with a button to open it full screen", async () => {
    mockApi({
      "GET /api/collections/3": { body: events },
      "GET /api/collections/3/records/1": { body: interview },
      "GET /api/senders/contact-photos": { body: [] },
      "GET /api/senders/logos": { body: [] },
    });
    const onOpenChange = vi.fn();
    renderWithProviders(<RecordSheet collectionId={3} recordId={1} onOpenChange={onOpenChange} />);

    const sheet = await screen.findByRole("dialog", { name: "Interview at Acme" });
    expect(within(sheet).getByText("Calendar")).toBeInTheDocument();
    const fullScreen = within(sheet).getByRole("link", { name: "Open full screen" });
    expect(fullScreen).toHaveAttribute("href", "/collections/3/records/1");
    // Same size and row as the close button: both pinned to the sheet's top right.
    const close = within(sheet).getByRole("button", { name: "Close" });
    expect(fullScreen).toHaveClass("size-7");
    expect(close).toHaveClass("size-7");
    expect(fullScreen.parentElement).toHaveClass("absolute", "top-3");
    const details = await within(sheet).findByRole("form", { name: "Details" });
    expect(within(details).getByRole("textbox", { name: "Location" })).toHaveValue("Acme HQ");
    expect(within(sheet).getByRole("tablist", { name: "Record sections" })).toBeInTheDocument();

    await userEvent.keyboard("{Escape}");
    expect(onOpenChange).toHaveBeenCalledWith(false);
  });
});
