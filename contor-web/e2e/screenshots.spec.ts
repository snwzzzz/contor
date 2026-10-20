import { expect, test, type Page } from "@playwright/test";
import { STORAGE_STATE } from "./support/backend";

/**
 * Opt-in visual baseline for design work (not an assertion suite): E2E_SCREENSHOTS=true writes
 * e2e/.tmp/screenshots/<screen>-<theme>.png for the main screens in light and dark mode.
 */
test.skip(process.env.E2E_SCREENSHOTS !== "true", "Set E2E_SCREENSHOTS=true to capture screenshots");

const themes = ["light", "dark"] as const;

async function capture(page: Page, name: string, theme: (typeof themes)[number]) {
  await page.screenshot({ path: `e2e/.tmp/screenshots/${name}-${theme}.png`, fullPage: true });
}

for (const theme of themes) {
  test.describe(`${theme} theme`, () => {
    test.use({ colorScheme: theme, viewport: { width: 1440, height: 900 } });

    test(`login (${theme})`, async ({ browser }) => {
      const context = await browser.newContext({ colorScheme: theme, viewport: { width: 1440, height: 900 } });
      const page = await context.newPage();
      await page.goto("/login");
      await expect(page.getByRole("button", { name: "Log in" })).toBeVisible();
      await capture(page, "login", theme);
      await context.close();
    });

    test.describe("signed in", () => {
      test.use({ storageState: STORAGE_STATE });

      test(`home at desktop and phone width (${theme})`, async ({ page }) => {
        for (const [width, height] of [[1920, 1080], [390, 844]]) {
          await page.setViewportSize({ width, height });
          await page.goto("/");
          await expect(page.getByRole("heading", { level: 2, name: "Navigate" })).toBeVisible();
          await expect(page.locator("main [data-slot=skeleton]")).toHaveCount(0);
          // The page scrolls inside <main>: capture the whole of it, not only the viewport.
          await page.locator("main").evaluate((main) => {
            main.style.overflow = "visible";
            main.parentElement?.style.setProperty("height", "auto");
            main.parentElement?.style.setProperty("overflow", "visible");
          });
          await capture(page, `home-${width}`, theme);
        }
      });

      test(`shell and palette (${theme})`, async ({ page }) => {
        await page.goto("/inbox");
        // Current user loaded = page hydrated.
        await expect(page.getByRole("button", { name: /e2e_owner/ })).toBeVisible();
        await capture(page, "inbox", theme);

        await page.goto("/inbox?settings=accounts");
        await expect(page.getByRole("dialog", { name: "Settings" })).toBeVisible();
        await capture(page, "settings", theme);

        await page.keyboard.press("ControlOrMeta+k");
        await expect(page.getByRole("listbox")).toBeVisible();
        await capture(page, "command-palette", theme);
      });
    });
  });
}
