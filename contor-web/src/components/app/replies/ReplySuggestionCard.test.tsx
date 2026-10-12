import { screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { ProposalDto } from "@/lib/api/generated/model/proposalDto";
import { renderWithProviders } from "@/test/render";
import { ReplySuggestionCard } from "./ReplySuggestionCard";

function proposal(payload: Record<string, unknown>): ProposalDto {
  return {
    id: 21,
    kind: "DRAFT_REPLY",
    status: "PENDING",
    importance: "NORMAL",
    importanceScore: 55,
    createdAt: "2026-10-07T08:00:00Z",
    payload: { emailId: 8, to: [{ address: "anna@acme.com" }], cc: [], subject: "Re: Workshop", body: "Gerne am Dienstag. [Preis]", ...payload },
  };
}

describe("ReplySuggestionCard", () => {
  it("lists the questions of the email and which ones the draft answers", () => {
    renderWithProviders(
      <ReplySuggestionCard
        proposal={proposal({
          questions: [
            { question: "Wann hast du Zeit?", answered: true },
            { question: "Was kostet der Workshop?", answered: false },
            { question: "Kommst du allein?", answered: null },
          ],
        })}
      />,
    );

    const list = screen.getByRole("list", { name: "Questions in the email" });
    const items = within(list).getAllByRole("listitem");
    expect(items[0]).toHaveTextContent("Wann hast du Zeit?");
    expect(within(items[0]).getByLabelText("Answered")).toBeInTheDocument();
    expect(within(items[1]).getByLabelText("Still open: fill in the placeholder")).toBeInTheDocument();
    expect(within(items[2]).queryByLabelText(/Answered|Still open/)).not.toBeInTheDocument();
  });

  it("shows the free times the draft offers", () => {
    renderWithProviders(
      <ReplySuggestionCard
        proposal={proposal({
          intent: "SCHEDULE",
          proposedSlots: [
            { start: "2026-10-13T11:00:00+02:00", end: "2026-10-13T12:00:00+02:00" },
            { start: "2026-10-14T09:00:00+02:00", end: "2026-10-14T10:00:00+02:00" },
          ],
        })}
      />,
    );

    const times = screen.getByRole("list", { name: "Free times offered" });
    expect(within(times).getAllByRole("listitem")).toHaveLength(2);
    expect(times).toHaveTextContent(/13 Oct/);
  });

  it("calls a polite reply optional", () => {
    renderWithProviders(<ReplySuggestionCard proposal={proposal({ intent: "COURTESY", courtesyKind: "REJECTION_THANKS" })} />);

    expect(screen.getByText("Optional reply")).toBeInTheDocument();
    expect(screen.queryByText("Draft reply")).not.toBeInTheDocument();
  });

  it("shows neither for a draft without questions or times", () => {
    renderWithProviders(<ReplySuggestionCard proposal={proposal({})} />);

    expect(screen.queryByRole("list", { name: "Questions in the email" })).not.toBeInTheDocument();
    expect(screen.queryByRole("list", { name: "Free times offered" })).not.toBeInTheDocument();
  });
});
