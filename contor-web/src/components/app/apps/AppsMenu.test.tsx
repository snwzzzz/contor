import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { ExternalApiDto } from "@/lib/api/generated/model/externalApiDto";
import { mockApi } from "@/test/api-mock";
import { resetNavigation, router } from "@/test/next-navigation";
import { renderWithProviders } from "@/test/render";
import { AppsMenu } from "./AppsMenu";

vi.mock("next/navigation", () => import("@/test/next-navigation"));

function app(id: number, name: string, overrides: Partial<ExternalApiDto> = {}): ExternalApiDto {
  return {
    id,
    name,
    slug: `app_${id}`,
    baseUrl: `https://api.${name.toLowerCase()}.example/v1`,
    authKind: "NONE",
    authConfig: {},
    hasSecret: false,
    headers: [],
    secretHeaderNames: [],
    pollIntervalMinutes: 360,
    enabled: true,
    aiAnalysis: true,
    status: "ACTIVE",
    failureCount: 0,
    dailyRequestLimit: 200,
    endpointCount: 1,
    createdAt: "2026-10-28T08:00:00Z",
    ...overrides,
  };
}

async function openMenu() {
  renderWithProviders(<AppsMenu />);
  await userEvent.click(screen.getByRole("button", { name: "Apps" }));
  return screen.findByRole("menu");
}

describe("AppsMenu", () => {
  beforeEach(() => {
    resetNavigation("/calendar");
    vi.useFakeTimers({ toFake: ["Date"], now: new Date("2026-10-29T12:00:00Z") });
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("lists the apps with their host and when they were last called", async () => {
    mockApi({
      "GET /api/external-apis": {
        body: [app(1, "Shop", { lastCalledAt: "2026-10-29T09:00:00Z" }), app(2, "Jobs")],
      },
    });

    const menu = await openMenu();

    const shop = await within(menu).findByRole("menuitem", { name: /Shop/ });
    expect(shop).toHaveTextContent("api.shop.example");
    expect(shop).toHaveTextContent("3 hr. ago");
    expect(within(menu).getByRole("menuitem", { name: /Jobs/ })).toHaveTextContent("Not called yet");
  });

  it("marks apps that need attention, and only those", async () => {
    mockApi({
      "GET /api/external-apis": {
        body: [app(1, "Shop", { status: "FAILING" }), app(2, "Jobs", { status: "PAUSED" }), app(3, "Weather")],
      },
    });

    const menu = await openMenu();

    expect(await within(menu).findByRole("menuitem", { name: /Shop.*Failing/ })).toBeInTheDocument();
    expect(within(menu).getByRole("menuitem", { name: /Jobs.*Paused/ })).toBeInTheDocument();
    expect(within(menu).getByRole("menuitem", { name: /Weather/ })).not.toHaveTextContent(/Failing|Paused|Needs setup/);
  });

  it("opens an app's modal over the current page with the keyboard", async () => {
    mockApi({ "GET /api/external-apis": { body: [app(1, "Shop"), app(2, "Jobs")] } });
    const menu = await openMenu();
    await within(menu).findByRole("menuitem", { name: /Shop/ });

    await userEvent.keyboard("{ArrowDown}{ArrowDown}{Enter}");

    expect(router.push).toHaveBeenCalledWith("/calendar?app=2", { scroll: false });
  });

  it("adds an app from the footer", async () => {
    mockApi({ "GET /api/external-apis": { body: [app(1, "Shop")] } });
    const menu = await openMenu();

    await userEvent.click(await within(menu).findByRole("menuitem", { name: "Add app…" }));

    expect(router.push).toHaveBeenCalledWith("/calendar?app=new", { scroll: false });
  });

  it("leads to the apps page from the footer", async () => {
    mockApi({ "GET /api/external-apis": { body: [app(1, "Shop")] } });
    const menu = await openMenu();

    await userEvent.click(await within(menu).findByRole("menuitem", { name: "Manage apps" }));

    expect(router.push).toHaveBeenCalledWith("/apps");
  });

  it("explains the empty list and offers to add the first app", async () => {
    mockApi({ "GET /api/external-apis": { body: [] } });
    const menu = await openMenu();

    expect(await within(menu).findByText("No apps yet")).toBeInTheDocument();
    await userEvent.click(within(menu).getByRole("menuitem", { name: "Add app" }));

    expect(router.push).toHaveBeenCalledWith("/calendar?app=new", { scroll: false });
  });

  it("searches by name and host once there are more than eight apps", async () => {
    const names = ["Shop", "Jobs", "Weather", "Bank", "Mail", "News", "Maps", "Music", "Books"];
    mockApi({ "GET /api/external-apis": { body: names.map((name, index) => app(index + 1, name)) } });
    const menu = await openMenu();

    await userEvent.type(await within(menu).findByRole("searchbox", { name: "Search apps" }), "mu");

    expect(within(menu).getAllByRole("menuitem").map((item) => item.textContent)).toEqual([
      expect.stringContaining("Music"),
      "Add app…",
      "Manage apps",
    ]);
  });

  it("shows no search for a short list", async () => {
    mockApi({ "GET /api/external-apis": { body: [app(1, "Shop")] } });
    const menu = await openMenu();
    await within(menu).findByRole("menuitem", { name: /Shop/ });

    expect(within(menu).queryByRole("searchbox")).not.toBeInTheDocument();
  });
});
