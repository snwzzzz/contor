import { expect, test } from "@playwright/test";
import { STORAGE_STATE } from "./support/backend";

test.use({ storageState: STORAGE_STATE });

test.describe("app shell", () => {
  test("sidebar navigates every section and lists the seeded collections", async ({ page }) => {
    await page.goto("/inbox");
    const sidebar = page.getByRole("navigation", { name: "Main" });

    for (const [link, heading] of [
      ["Proposals", "Proposals"],
      ["Relations", "Relations"],
      ["Queries", "Queries"],
      ["Rules & Labels", "Rules & Labels"],
      ["Security", "Security"],
    ]) {
      await sidebar.getByRole("link", { name: new RegExp(`^${link}`) }).click();
      await expect(page.getByRole("heading", { level: 1, name: heading })).toBeVisible();
    }
    await expect(sidebar.getByRole("link", { name: "People" })).toBeVisible();
    await expect(sidebar.getByRole("link", { name: "Organizations" })).toBeVisible();
  });

  test("command palette jumps to a collection", async ({ page }) => {
    await page.goto("/inbox");
    // Sidebar data loaded = page hydrated, so the keyboard shortcut is wired.
    await expect(page.getByRole("navigation", { name: "Main" }).getByRole("link", { name: "People" })).toBeVisible();

    await page.keyboard.press("ControlOrMeta+k");
    await page.getByPlaceholder("Search pages, collections and actions…").fill("organ");
    await page.keyboard.press("Enter");

    await expect(page).toHaveURL(/\/collections\/\d+$/);
    await expect(page.getByRole("heading", { level: 1, name: "Organizations" })).toBeVisible();
  });

  test("dark mode can be switched on", async ({ page }) => {
    await page.goto("/inbox");

    await page.getByRole("button", { name: /e2e_owner/ }).click();
    await page.getByRole("menuitem", { name: "Dark" }).click();

    await expect(page.locator("html")).toHaveClass(/dark/);
  });

  test("pages and the command palette log no errors", async ({ page }) => {
    const errors: string[] = [];
    page.on("console", (message) => {
      if (message.type() === "error") errors.push(message.text());
    });
    page.on("pageerror", (error) => errors.push(error.message));

    for (const path of ["/inbox", "/proposals"]) {
      await page.goto(path);
      await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
    }
    // Settings are a modal over the inbox; the old URLs redirect into it.
    for (const [path, section] of [["/settings", "Accounts"], ["/settings/security", "Security"]]) {
      await page.goto(path);
      await expect(page.getByRole("dialog", { name: "Settings" }).getByRole("heading", { name: section })).toBeVisible();
    }
    await page.keyboard.press("Escape");
    await expect(page.getByRole("navigation", { name: "Main" }).getByRole("link", { name: "People" })).toBeVisible();
    await page.keyboard.press("ControlOrMeta+k");
    await expect(page.getByPlaceholder("Search pages, collections and actions…")).toBeVisible();
    await page.keyboard.press("Escape");

    expect(errors).toEqual([]);
  });

  test("logout ends the session", async ({ browser }) => {
    const context = await browser.newContext({ storageState: STORAGE_STATE });
    const page = await context.newPage();
    await page.goto("/inbox");

    await page.getByRole("button", { name: /e2e_owner/ }).click();
    await page.getByRole("menuitem", { name: "Log out" }).click();
    await expect(page).toHaveURL(/\/login$/);

    await page.goto("/inbox");
    await expect(page).toHaveURL(/\/login/);
    await context.close();
  });
});
