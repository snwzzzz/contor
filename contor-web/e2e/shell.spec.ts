import { expect, test } from "@playwright/test";
import { STORAGE_STATE } from "./support/backend";

test.use({ storageState: STORAGE_STATE });

test.describe("app shell", () => {
  test("navigation drawer opens over the page, navigates every section and searches pages", async ({ page }) => {
    await page.goto("/inbox");
    const nav = page.getByRole("dialog", { name: "Navigation" });

    for (const [link, heading] of [
      ["Proposals", "Proposals"],
      ["Relations", "Relations"],
      ["Queries", "Queries"],
      ["Rules & Labels", "Rules & Labels"],
      ["Home", "Welcome to contor"],
    ]) {
      await page.getByRole("button", { name: "Menu" }).click();
      await nav.getByRole("link", { name: new RegExp(`^${link}`) }).click();
      await expect(page.getByRole("heading", { level: 1, name: heading })).toBeVisible();
      await expect(nav).toBeHidden();
    }

    await page.getByRole("button", { name: "Menu" }).click();
    await expect(nav.getByRole("link", { name: "People" })).toBeVisible();
    await nav.getByRole("searchbox", { name: "Search pages" }).fill("organ");
    await expect(nav.getByRole("link")).toHaveText(["Organizations"]);
    // A click outside closes it.
    await page.mouse.click(600, 400);
    await expect(nav).toBeHidden();
  });

  test("top bar opens the app launcher, chat and settings", async ({ page }) => {
    await page.goto("/");
    const bar = page.getByRole("banner");

    await bar.getByRole("button", { name: "Apps" }).click();
    await expect(page.getByText("No apps yet")).toBeVisible();
    await page.keyboard.press("Escape");

    await bar.getByRole("link", { name: "Settings" }).click();
    await expect(page.getByRole("dialog", { name: "Settings" }).getByRole("heading", { name: "Accounts" })).toBeVisible();
    await page.keyboard.press("Escape");

    await bar.getByRole("link", { name: "Chat" }).click();
    await expect(page.getByRole("heading", { level: 1, name: "Chat" })).toBeVisible();
  });

  test("home page shows quick actions, news, proposals and mailboxes", async ({ page }) => {
    await page.goto("/");

    await expect(page.getByRole("heading", { level: 1, name: "Welcome to contor" })).toBeVisible();
    for (const section of ["Quick actions", "Aktuelles", "Actions", "Connected mailboxes"]) {
      await expect(page.getByRole("heading", { level: 2, name: section, exact: true })).toBeVisible();
    }
    await page.getByRole("link", { name: /^View mailbox/ }).click();
    await expect(page).toHaveURL(/\/inbox$/);
  });

  test("command palette jumps to a collection", async ({ page }) => {
    await page.goto("/inbox");
    // Current user loaded = page hydrated, so the keyboard shortcut is wired.
    await expect(page.getByRole("button", { name: /e2e_owner/ })).toBeVisible();

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

    for (const path of ["/", "/inbox", "/proposals", "/chat"]) {
      await page.goto(path);
      await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
    }
    // Settings are a modal over the inbox; the old URLs redirect into it.
    for (const [path, section] of [["/settings", "Accounts"], ["/settings/security", "Security"]]) {
      await page.goto(path);
      await expect(page.getByRole("dialog", { name: "Settings" }).getByRole("heading", { name: section })).toBeVisible();
    }
    await page.keyboard.press("Escape");
    await page.getByRole("button", { name: "Menu" }).click();
    await expect(page.getByRole("dialog", { name: "Navigation" }).getByRole("link", { name: "People" })).toBeVisible();
    await page.keyboard.press("Escape");
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
