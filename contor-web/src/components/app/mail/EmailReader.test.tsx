import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import type { EmailDto } from "@/lib/api/generated/model/emailDto";
import { renderWithProviders } from "@/test/render";
import { EmailReader } from "./EmailReader";

const EMAIL: EmailDto = {
  id: 7,
  accountId: 1,
  fromAddress: "noreply@treatwell.at",
  fromName: "Treatwell",
  toRecipients: [{ name: "Danijel", address: "me@example.com" }],
  ccRecipients: [{ address: "salon@example.com" }],
  subject: "Booking confirmed",
  bodyText: "Your booking is confirmed.",
  bodyHtml: "<p>Your <b>booking</b> is confirmed.</p>",
  receivedAt: "2026-08-24T15:03:00Z",
  read: true,
  hasAttachments: true,
  processingState: "DONE",
  labels: [{ labelId: 3, slug: "bookings", name: "Bookings", color: "#00ff00", source: "AI" }],
};

describe("EmailReader", () => {
  it("shows subject, sender, recipients, labels and attachments", () => {
    renderWithProviders(<EmailReader email={EMAIL} />);

    expect(screen.getByRole("heading", { name: "Booking confirmed" })).toBeInTheDocument();
    expect(screen.getByText("Treatwell")).toBeInTheDocument();
    expect(screen.getByText("noreply@treatwell.at")).toBeInTheDocument();
    expect(screen.getByText("Danijel")).toBeInTheDocument();
    expect(screen.getByText("salon@example.com")).toBeInTheDocument();
    expect(screen.getByText("Bookings")).toBeInTheDocument();
    expect(screen.getByText(/attachment/i)).toBeInTheDocument();
  });

  it("shows formatted mail and switches to plain text", async () => {
    renderWithProviders(<EmailReader email={EMAIL} />);

    expect(screen.getByTitle("Email content")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Plain text" }));

    expect(screen.getByText("Your booking is confirmed.")).toBeInTheDocument();
    expect(screen.queryByTitle("Email content")).not.toBeInTheDocument();
  });

  it("offers no switch when there is only text", () => {
    renderWithProviders(<EmailReader email={{ ...EMAIL, bodyHtml: undefined }} />);

    expect(screen.getByText("Your booking is confirmed.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Plain text" })).not.toBeInTheDocument();
  });
});
