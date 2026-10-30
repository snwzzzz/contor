import { expect, test } from "@playwright/test";
import { STORAGE_STATE } from "./support/backend";

test.use({ storageState: STORAGE_STATE });

/**
 * Adding an app (external API) in the modal and finding it in the top bar's External APIs menu. No test call here: the backend
 * only calls public HTTPS hosts and has no switch for local ones on purpose (SSRF); the call path is covered by
 * ExternalApiPollIntegrationTest and the Vitest preview tests.
 */
test.describe("apps", () => {
  test("adds an app with an endpoint, lists it in the External APIs menu, opens its page, edits and deletes it", async ({ page }) => {
    await page.goto("/calendar");
    const bar = page.getByRole("banner");

    await bar.getByRole("button", { name: "External APIs" }).click();
    await page.getByRole("menuitem", { name: "Add app" }).click();
    const dialog = page.getByRole("dialog", { name: "Add app" });
    await expect(page).toHaveURL(/\/calendar\?app=new$/);

    await dialog.getByLabel("Name").fill("E2E Shop");
    await dialog.getByLabel("Base URL").fill("https://api.shop.example/v1");
    await dialog.getByRole("button", { name: "Next step" }).click();
    await dialog.getByLabel("Authentication").selectOption("BEARER");
    await dialog.getByLabel("Token").fill("e2e-token");
    await dialog.getByRole("button", { name: "Next step" }).click();
    await dialog.getByRole("button", { name: "Add endpoint" }).click();
    await dialog.getByLabel("Endpoint name").fill("Orders");
    await dialog.getByLabel("Path").fill("/orders");
    await dialog.getByRole("button", { name: "Keep endpoint" }).click();
    await dialog.getByRole("button", { name: "Next step" }).click();
    await dialog.getByRole("radio", { name: "Every hour" }).check();
    await dialog.getByRole("button", { name: "Add app" }).click();

    await expect(dialog).toBeHidden();
    await expect(page).toHaveURL(/\/calendar$/);

    await bar.getByRole("button", { name: "External APIs" }).click();
    const item = page.getByRole("menuitem", { name: /E2E Shop/ });
    await expect(item).toContainText("api.shop.example");
    await expect(item).toContainText("Not called yet");
    await item.press("Enter");
    await expect(page).toHaveURL(/\/apps\/\d+$/);
    await expect(page.getByRole("heading", { name: "E2E Shop" })).toBeVisible();
    await page.getByRole("link", { name: "Edit" }).click();

    const edit = page.getByRole("dialog", { name: "E2E Shop" });
    await edit.getByRole("tab", { name: "Auth & headers" }).click();
    await expect(edit.getByText("Token stored")).toBeVisible();
    await expect(page.getByText("e2e-token")).toHaveCount(0);
    await edit.getByRole("tab", { name: "Endpoints" }).click();
    await expect(edit.getByRole("listitem", { name: "Orders" })).toContainText("GET/orders");

    await edit.getByRole("button", { name: "Delete app" }).click();
    await edit.getByRole("button", { name: "Yes, delete E2E Shop" }).click();
    await expect(edit).toBeHidden();

    await bar.getByRole("button", { name: "External APIs" }).click();
    await expect(page.getByText("No apps yet")).toBeVisible();
  });
});
