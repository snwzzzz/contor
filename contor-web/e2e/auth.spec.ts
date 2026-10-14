import { expect, test } from "@playwright/test";
import { OWNER } from "./support/backend";

test.describe("authentication", () => {
  test("setup is gone once the owner exists", async ({ page }) => {
    await page.goto("/setup");

    await expect(page).toHaveURL(/\/login$/);
  });

  test("protected pages redirect to the login and come back afterwards", async ({ page }) => {
    await page.goto("/proposals");
    await expect(page).toHaveURL(/\/login\?next=%2Fproposals$/);

    await page.getByLabel("Username").fill(OWNER.username);
    await page.getByLabel("Password").fill(OWNER.password);
    await page.getByRole("button", { name: "Log in" }).click();

    await expect(page).toHaveURL(/\/proposals$/);
    await expect(page.getByRole("heading", { level: 1, name: "Proposals" })).toBeVisible();
  });

  test("wrong credentials show one generic message", async ({ page }) => {
    await page.goto("/login");

    await page.getByLabel("Username").fill(OWNER.username);
    await page.getByLabel("Password").fill("Wrong-Password-1!");
    await page.getByRole("button", { name: "Log in" }).click();

    await expect(page.locator("form").getByRole("alert")).toHaveText("Invalid username or password");
    await expect(page).toHaveURL(/\/login$/);
  });

  test("pages are served with a strict content security policy that the page itself does not violate", async ({ page }) => {
    const violations: string[] = [];
    page.on("console", (message) => {
      if (message.text().includes("Content Security Policy")) violations.push(message.text());
    });
    const response = await page.goto("/login");
    await expect(page.getByRole("button", { name: "Log in" })).toBeEnabled();

    const csp = response?.headers()["content-security-policy"] ?? "";
    expect(csp).toMatch(/script-src 'self' 'nonce-[^']+' 'strict-dynamic'/);
    expect(csp).toContain("frame-ancestors 'none'");
    expect(response?.headers()["x-frame-options"]).toBe("DENY");
    expect(violations).toEqual([]);
  });
});
