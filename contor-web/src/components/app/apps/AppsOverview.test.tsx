import { screen, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { apiError, mockApi } from "@/test/api-mock";
import { appDto } from "@/test/external-api-fixtures";
import { resetNavigation } from "@/test/next-navigation";
import { renderWithProviders } from "@/test/render";
import { AppsOverview } from "./AppsOverview";

vi.mock("next/navigation", () => import("@/test/next-navigation"));

describe("AppsOverview", () => {
  beforeEach(() => {
    resetNavigation("/apps");
    vi.useFakeTimers({ toFake: ["Date"], now: new Date("2026-10-29T12:00:00Z") });
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("lists the apps with their host, health, interval, last call and endpoints", async () => {
    mockApi({
      "GET /api/external-apis": {
        body: [
          appDto(7, "Shop", { lastCalledAt: "2026-10-29T09:00:00Z", pollIntervalMinutes: 60 }),
          appDto(8, "Jobs", { status: "FAILING", lastError: "The app answered with HTTP 500" }),
          appDto(9, "Weather", { enabled: false }),
        ],
      },
    });

    renderWithProviders(<AppsOverview />);

    const shop = (await screen.findByRole("link", { name: "Shop" })).closest("tr");
    expect(screen.getByRole("link", { name: "Shop" })).toHaveAttribute("href", "/apps/7");
    expect(shop).toHaveTextContent("api.shop.example");
    expect(shop).toHaveTextContent("Active");
    expect(shop).toHaveTextContent("Every hour");
    expect(shop).toHaveTextContent("3 hr. ago");
    expect(within(shop as HTMLElement).getByText("2")).toBeInTheDocument();
    const jobs = screen.getByRole("link", { name: "Jobs" }).closest("tr");
    expect(jobs).toHaveTextContent("Failing");
    expect(jobs).toHaveTextContent("Not called yet");
    expect(screen.getByRole("link", { name: "Weather" }).closest("tr")).toHaveTextContent("Off");
  });

  it("explains the empty page and offers to add the first app", async () => {
    mockApi({ "GET /api/external-apis": { body: [] } });

    renderWithProviders(<AppsOverview />);

    expect(await screen.findByText("No apps yet")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Add app" })).toHaveAttribute("href", "/apps?app=new");
  });

  it("shows why the apps could not be loaded", async () => {
    mockApi({ "GET /api/external-apis": apiError(500, "Something went wrong") });

    renderWithProviders(<AppsOverview />);

    expect(await screen.findByText("Something went wrong")).toBeInTheDocument();
  });
});
