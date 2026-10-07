import { expect, test } from "@playwright/test";

test("app starts and renders the contor home page", async ({ page }) => {
  await page.goto("/");
  await expect(page).toHaveTitle(/contor/);
  await expect(page.getByRole("heading", { level: 1, name: "contor" })).toBeVisible();
});
