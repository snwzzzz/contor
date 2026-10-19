import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { mockApi } from "@/test/api-mock";
import { resetNavigation, router } from "@/test/next-navigation";
import { renderWithProviders } from "@/test/render";
import { RecordReports } from "./RecordReports";

vi.mock("next/navigation", () => import("@/test/next-navigation"));

const READY = { reachable: true, ready: true, models: [] };

function summary(id: number, title: string, extra: Record<string, unknown> = {}) {
  return { id, title, prompt: `Question ${id}`, state: "READY", summary: `Summary ${id}`, claimsTotal: 1, claimsVerified: 1, followUpCount: 0,
    createdAt: "2026-10-14T10:00:00Z", ...extra };
}

function report(id: number, title: string) {
  return { id, collectionId: 1, recordId: 4711, prompt: `Question ${id}`, title, state: "READY", records: [], emailCount: 0,
    claimsTotal: 0, claimsVerified: 0, claimsRemoved: 0, createdAt: "2026-10-14T10:00:00Z", sections: [], sources: [],
    removedClaims: [], followUps: [] };
}

const LIST = [summary(5, "Order 4711 review"), summary(3, "Supplier overview", { prompt: "Who supplies us?" })];

function api(extra: Record<string, unknown> = {}) {
  return mockApi({
    "GET /api/ai/status": { body: READY },
    "GET /api/collections/1/records/4711/reports": { body: LIST },
    "GET /api/collections/1": { body: { id: 1, slug: "people", name: "People" } },
    "GET /api/collections/1/records/4711": { body: { id: 4711, collectionId: 1, title: "Anna Berger", values: {}, extra: {}, createdAt: "", createdBy: "USER" } },
    "GET /api/reports/5": { body: report(5, "Order 4711 review") },
    "GET /api/reports/3": { body: report(3, "Supplier overview") },
    ...extra,
  });
}

function renderReports() {
  return renderWithProviders(<RecordReports collectionId={1} recordId={4711} recordTitle="Anna Berger" />);
}

describe("RecordReports", () => {
  beforeEach(() => resetNavigation("/collections/1/records/4711"));
  afterEach(() => window.localStorage.clear());

  it("starts with the question and the input, the reports listed in a side list", async () => {
    api();
    renderReports();

    expect(screen.getByRole("heading", { name: "What do you want to know about Anna Berger?" })).toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: "Ask about this record" })).toBeInTheDocument();
    const list = screen.getByRole("complementary", { name: "Report list" });
    expect(await within(list).findAllByRole("button", { name: /review|overview/ })).toHaveLength(2);
    // Fills the page (full width, the screen height); the divider runs the full height; the start is centered.
    const workspace = screen.getByRole("region", { name: "Reports" });
    // Fills the tab panel (the record page makes it the rest of the screen); no fixed height guess.
    expect(workspace).toHaveClass("lg:h-full");
    expect(workspace.className).not.toMatch(/100dvh/);
    expect(list).toHaveClass("lg:h-full", "lg:border-r");
    expect(screen.getByRole("heading", { name: /What do you want to know/ }).parentElement).toHaveClass("mx-auto", "justify-center");
  });

  it("opens a report from the list next to the list, with a way to open it full screen", async () => {
    api();
    renderReports();
    const list = screen.getByRole("complementary", { name: "Report list" });

    await userEvent.click(await within(list).findByRole("button", { name: /Supplier overview/ }));

    expect(within(list).getByRole("button", { name: /Supplier overview/ })).toHaveAttribute("aria-current", "true");
    expect(await screen.findByRole("heading", { level: 2, name: "Supplier overview" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Open full screen" })).toHaveAttribute("href", "/collections/1/records/4711/reports/3");
    // Embedded in the record page: no second set of breadcrumbs.
    expect(screen.queryByRole("navigation", { name: "Breadcrumb" })).not.toBeInTheDocument();
  });

  it("searches the reports by title and request", async () => {
    api();
    renderReports();
    const list = screen.getByRole("complementary", { name: "Report list" });
    await within(list).findByRole("button", { name: /Supplier overview/ });

    await userEvent.type(within(list).getByRole("searchbox", { name: "Search reports" }), "supplies");

    expect(within(list).getAllByRole("button", { name: /review|overview/ }).map((button) => button.textContent)).toEqual([
      expect.stringContaining("Supplier overview"),
    ]);
  });

  it("slides the list in and out, the list and the detail scroll on their own and never sideways", async () => {
    api();
    renderReports();
    const workspace = screen.getByRole("region", { name: "Reports" });
    expect(workspace).toHaveClass("lg:transition-[grid-template-columns]", "lg:grid-cols-[16rem_minmax(0,1fr)]");
    const list = screen.getByRole("complementary", { name: "Report list" });
    expect(list.querySelector("ul, p")?.closest("[data-slot=report-list-scroll]")).toHaveClass("overflow-y-auto");
    expect(screen.getByTestId("report-detail")).toHaveClass("lg:overflow-y-auto", "lg:overflow-x-hidden");

    await userEvent.click(screen.getByRole("button", { name: "Hide report list" }));

    // Still mounted while it slides away, but hidden from everyone (inert).
    expect(workspace).toHaveClass("lg:grid-cols-[0rem_minmax(0,1fr)]");
    expect(screen.queryByRole("complementary", { name: "Report list" })).not.toBeInTheDocument();
    expect(workspace.querySelector("aside")).toHaveAttribute("inert");
  });

  it("keeps the layout still when the list closes: one toggle at the same place in both states", async () => {
    api();
    renderReports();
    const detail = screen.getByTestId("report-detail");
    const hide = screen.getByRole("button", { name: "Hide report list" });
    expect(detail.firstElementChild).toContainElement(hide);

    await userEvent.click(hide);

    const show = screen.getByRole("button", { name: "Show report list" });
    expect(show).toBe(hide);
    expect(detail.firstElementChild).toContainElement(show);
  });

  it("closes and reopens the list, and remembers it", async () => {
    api();
    const { unmount } = renderReports();

    await userEvent.click(screen.getByRole("button", { name: "Hide report list" }));
    expect(screen.queryByRole("complementary", { name: "Report list" })).not.toBeInTheDocument();
    unmount();

    renderReports();
    expect(screen.queryByRole("complementary", { name: "Report list" })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Show report list" }));
    expect(screen.getByRole("complementary", { name: "Report list" })).toBeInTheDocument();
  });

  it("sends the request and shows the new report right here while the assistant writes it", async () => {
    const calls = api({
      "POST /api/collections/1/records/4711/reports": { status: 202, body: summary(12, "New report", { state: "QUEUED" }) },
      "GET /api/reports/12": { body: { ...report(12, "New report"), state: "DRAFTING" } },
    });
    const user = userEvent.setup();
    renderReports();

    await user.type(screen.getByRole("textbox", { name: "Ask about this record" }), "What went wrong?{Enter}");

    expect(await screen.findByRole("heading", { level: 2, name: "New report" })).toBeInTheDocument();
    expect(calls.find((call) => call.method === "POST")?.body).toEqual({ prompt: "What went wrong?" });
    expect(router.push).not.toHaveBeenCalled();
  });

  it("sends a suggestion right away", async () => {
    const calls = api({
      "POST /api/collections/1/records/4711/reports": { status: 202, body: summary(13, "Summary", { state: "QUEUED" }) },
      "GET /api/reports/13": { body: report(13, "Summary") },
    });
    renderReports();

    await userEvent.click(screen.getByRole("button", { name: "Summarize this record" }));

    expect(await screen.findByRole("heading", { level: 2, name: "Summary" })).toBeInTheDocument();
    expect(calls.find((call) => call.method === "POST")?.body).toEqual({ prompt: "Summarize this record" });
  });

  it("goes back to a new question with New report", async () => {
    api();
    renderReports();
    const list = screen.getByRole("complementary", { name: "Report list" });
    await userEvent.click(await within(list).findByRole("button", { name: /Order 4711 review/ }));
    await screen.findByRole("heading", { level: 2, name: "Order 4711 review" });

    await userEvent.click(within(list).getByRole("button", { name: "New report" }));

    expect(screen.getByRole("heading", { name: "What do you want to know about Anna Berger?" })).toBeInTheDocument();
  });

  it("shows when a report is still being written", async () => {
    api({ "GET /api/collections/1/records/4711/reports": { body: [summary(5, "Order 4711 review", { state: "DRAFTING" })] } });
    renderReports();

    expect(await screen.findByText("Writing…")).toBeInTheDocument();
  });

  it("says when there is no report yet", async () => {
    api({ "GET /api/collections/1/records/4711/reports": { body: [] } });
    renderReports();

    expect(await screen.findByText("No reports yet")).toBeInTheDocument();
  });

  it("cannot ask while the model is not ready", async () => {
    api({ "GET /api/ai/status": { body: { reachable: false, ready: false, models: [] } } });
    renderReports();

    expect(await screen.findByText(/The AI model is not ready/)).toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: "Ask about this record" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Summarize this record" })).toBeDisabled();
  });
});
