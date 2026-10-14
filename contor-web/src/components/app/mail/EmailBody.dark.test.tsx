import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import type { EmailDto } from "@/lib/api/generated/model/emailDto";
import { renderWithProviders } from "@/test/render";
import { EmailBody } from "./EmailReader";

vi.mock("next-themes", () => ({ useTheme: () => ({ resolvedTheme: "dark" }) }));

const EMAIL: EmailDto = {
  id: 7,
  accountId: 1,
  toRecipients: [],
  ccRecipients: [],
  subject: "Booking confirmed",
  bodyHtml: "<p>Your booking is confirmed.</p>",
  receivedAt: "2026-08-24T15:03:00Z",
  read: true,
  hasAttachments: false,
  processingState: "DONE",
  labels: [],
};

describe("EmailBody in dark mode", () => {
  it("shows HTML mail with inverted colors and switches to the original colors", async () => {
    renderWithProviders(<EmailBody email={EMAIL} />);

    const frame = screen.getByTitle("Email content");
    expect(frame).toHaveAttribute("data-dark", "true");
    expect(frame).not.toHaveAttribute("data-paper");
    expect(frame.style.filter).toContain("invert");

    await userEvent.click(screen.getByRole("button", { name: "Original colors" }));

    expect(screen.getByTitle("Email content")).not.toHaveAttribute("data-dark");
    expect(screen.getByTitle("Email content")).toHaveAttribute("data-paper", "true");
    expect(screen.getByTitle("Email content").style.filter).toBe("");
    expect(screen.getByRole("button", { name: "Original colors" })).toHaveAttribute("aria-pressed", "true");
  });

  it("re-inverts pictures inside the mail so they keep their colors", () => {
    renderWithProviders(<EmailBody email={EMAIL} />);

    expect(screen.getByTitle("Email content").getAttribute("srcdoc")).toMatch(/html\.contor-dark img[^{]*\{[^}]*invert/);
  });
});
