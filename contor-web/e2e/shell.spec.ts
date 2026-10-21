import { expect, test } from "@playwright/test";
import { STORAGE_STATE } from "./support/backend";

test.use({ storageState: STORAGE_STATE });

/** The home page greets the signed-in user by time of day ("Welcome back" until the client knows the time). */
const GREETING = /^(Good (morning|afternoon|evening)|Welcome back), e2e_owner$/;

test.describe("app shell", () => {
  test("navigation drawer opens over the page, navigates every section and searches pages", async ({ page }) => {
    await page.goto("/inbox");
    const nav = page.getByRole("dialog", { name: "Navigation" });

    for (const [link, heading] of [
      ["Proposals", "Proposals"],
      ["Relations", "Relations"],
      ["Queries", "Queries"],
      ["Rules & Labels", "Rules & Labels"],
      ["Home", GREETING],
    ] as const) {
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

  test("top bar opens the app launcher, chat and settings (account menu)", async ({ page }) => {
    await page.goto("/");
    const bar = page.getByRole("banner");

    await bar.getByRole("button", { name: "Apps" }).click();
    await expect(page.getByText("No apps yet")).toBeVisible();
    await page.keyboard.press("Escape");

    await bar.getByRole("button", { name: /e2e_owner/ }).click();
    await page.getByRole("menuitem", { name: "Settings" }).click();
    await expect(page.getByRole("dialog", { name: "Settings" }).getByRole("heading", { name: "General", exact: true })).toBeVisible();
    await page.keyboard.press("Escape");

    await bar.getByRole("link", { name: "Chat" }).click();
    await expect(page.getByRole("heading", { level: 1, name: "Chat" })).toBeVisible();
  });

  test("home page greets, shows the numbers at a glance and every card", async ({ page }) => {
    await page.goto("/");

    await expect(page.getByRole("heading", { level: 1, name: GREETING })).toBeVisible();
    await expect(page.getByRole("status").filter({ hasText: /analyzing|waiting for review|categories|All caught up/ })).toBeVisible();
    for (const stat of ["Unread mail", "Pending proposals", "Open to-dos", "Today's events"]) {
      await expect(page.getByRole("link", { name: new RegExp(stat) })).toBeVisible();
    }
    for (const section of ["Recent activity", "Pending actions", "Today", "Connected mailboxes", "AI status", "Navigate"]) {
      await expect(page.getByRole("heading", { level: 2, name: section, exact: true })).toBeVisible();
    }

    await page.getByRole("tab", { name: "Unread" }).click();
    await expect(page.getByRole("tab", { name: "Unread" })).toHaveAttribute("aria-selected", "true");

    await page.getByRole("button", { name: "More" }).click();
    await expect(page.getByRole("listbox")).toBeVisible();
    await page.keyboard.press("Escape");

    await page.getByRole("link", { name: /^View mailbox/ }).click();
    await expect(page).toHaveURL(/\/inbox$/);
  });

  test("home page fits a phone screen without sideways scrolling", async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto("/");
    await expect(page.getByRole("heading", { level: 2, name: "Navigate" })).toBeVisible();

    const overflow = await page.locator("main").evaluate((main) => main.scrollWidth - main.clientWidth);
    expect(overflow).toBeLessThanOrEqual(0);
  });

  test("command palette jumps to a collection", async ({ page }) => {
    await page.goto("/inbox");
    // Current user loaded = page hydrated, so the keyboard shortcut is wired.
    await expect(page.getByRole("button", { name: /e2e_owner/ })).toBeVisible();

    await page.keyboard.press("ControlOrMeta+k");
    await page.getByPlaceholder("Search pages, collections, actions or mail…").fill("organ");
    // Collections load when the palette opens: pick the entry once it is there.
    await expect(page.getByRole("option", { name: /Organizations/ })).toBeVisible();
    await page.keyboard.press("Enter");

    await expect(page).toHaveURL(/\/collections\/\d+$/);
    await expect(page.getByRole("heading", { level: 1, name: "Organizations" })).toBeVisible();
  });

  test("dark mode can be switched on in Settings › General", async ({ page }) => {
    await page.goto("/inbox?settings=general");

    await page.getByRole("dialog", { name: "Settings" }).getByText("Dark", { exact: true }).click();

    await expect(page.locator("html")).toHaveClass(/dark/);
  });

  test("pages and the command palette log no errors", async ({ page }) => {
    const errors: string[] = [];
    page.on("console", (message) => {
      // "No analysis yet" is a 404 by design (GET /api/ai/analysis/latest); the browser logs it like a failure.
      const expected = message.location().url.endsWith("/api/ai/analysis/latest") && message.text().includes("404");
      if (message.type() === "error" && !expected) errors.push(`${new URL(page.url()).pathname}: ${message.text()} ${message.location().url}`);
    });
    page.on("pageerror", (error) => errors.push(error.message));

    for (const path of ["/", "/inbox", "/proposals", "/chat"]) {
      await page.goto(path);
      await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
    }
    // Settings are a modal over the inbox; the old URLs redirect into it.
    for (const [path, section] of [["/settings", "General"], ["/settings/security", "Security"]]) {
      await page.goto(path);
      await expect(page.getByRole("dialog", { name: "Settings" }).getByRole("heading", { name: section, exact: true })).toBeVisible();
    }
    await page.keyboard.press("Escape");
    await page.getByRole("button", { name: "Menu" }).click();
    await expect(page.getByRole("dialog", { name: "Navigation" }).getByRole("link", { name: "People" })).toBeVisible();
    await page.keyboard.press("Escape");
    await page.keyboard.press("ControlOrMeta+k");
    await expect(page.getByRole("listbox")).toBeVisible();
    await page.keyboard.press("Escape");

    expect(errors).toEqual([]);
  });

  test("my account shows who is signed in, from the account menu", async ({ page }) => {
    await page.goto("/inbox");

    await page.getByRole("button", { name: /e2e_owner/ }).click();
    await page.getByRole("menuitem", { name: "My account" }).click();

    await expect(page).toHaveURL(/\?settings=account$/);
    const dialog = page.getByRole("dialog", { name: "Settings" });
    await expect(dialog.getByRole("heading", { name: "My account" })).toBeVisible();
    await expect(dialog.getByLabel("Username")).toHaveText("e2e_owner");
    await expect(dialog.getByRole("button", { name: "Log out" })).toBeVisible();
  });

  test("AI models only manage the models; autonomy holds setup, mailboxes, permissions and the analysis", async ({ page }) => {
    await page.goto("/inbox?settings=ai");
    const dialog = page.getByRole("dialog", { name: "Settings" });
    await expect(dialog.getByRole("heading", { name: "Local model" })).toBeVisible();
    await expect(dialog.getByRole("link", { name: /AI setup|Set up the AI/ })).toHaveCount(0);

    await page.goto("/inbox?settings=autonomy");
    for (const section of ["Setup", "Mailboxes", "Permissions", "Inbox analysis"]) {
      await expect(dialog.getByRole("heading", { name: section, exact: true })).toBeVisible();
    }
    await expect(dialog.getByText("No mailbox connected yet.")).toBeVisible();
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
