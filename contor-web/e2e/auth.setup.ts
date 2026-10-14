import { expect, test as setup } from "@playwright/test";
import { OWNER, readSetupToken, STORAGE_STATE } from "./support/backend";

setup("first run: setup creates the owner, then the owner logs in", async ({ page }) => {
  await page.goto("/inbox");
  await expect(page).toHaveURL(/\/setup$/);

  await page.getByLabel("Setup token").fill(readSetupToken());
  await page.getByLabel("Username").fill(OWNER.username);
  await page.getByLabel("Email").fill(OWNER.email);
  await page.getByLabel("Password", { exact: true }).fill(OWNER.password);
  await page.getByLabel("Confirm password").fill(OWNER.password);
  await page.getByRole("button", { name: "Create owner account" }).click();

  await expect(page).toHaveURL(/\/login$/);
  await page.getByLabel("Username").fill(OWNER.username);
  await page.getByLabel("Password").fill(OWNER.password);
  await page.getByRole("button", { name: "Log in" }).click();

  await expect(page).toHaveURL(/\/inbox$/);
  await expect(page.getByRole("heading", { level: 1, name: "Inbox" })).toBeVisible();
  await page.context().storageState({ path: STORAGE_STATE });
});
