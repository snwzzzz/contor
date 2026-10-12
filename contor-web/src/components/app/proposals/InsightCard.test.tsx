import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import type { ProposalDto } from "@/lib/api/generated/model/proposalDto";
import { mockApi } from "@/test/api-mock";
import { renderWithProviders } from "@/test/render";
import { InsightCard } from "./InsightCard";

const insight: ProposalDto = {
  id: 31,
  kind: "INSIGHT",
  status: "PENDING",
  importance: "HIGH",
  importanceScore: 70,
  importanceReason: "a pattern across your mail; due within 2 days",
  createdAt: "2026-10-12T08:00:00Z",
  payload: {
    insightKey: "model:10,12",
    title: "StreamBox got more expensive",
    observation: "The October invoice is 14.99 EUR, September was 9.99 EUR.",
    whyItMatters: "A 50 % increase you may not have agreed to.",
    evidenceEmailIds: [12, 10],
    followUps: [
      { collectionId: 4, title: "Check the StreamBox plan", values: { due_date: "2026-10-15" } },
      { collectionId: 3, title: "Call StreamBox", values: { starts_at: "2026-10-16T10:00:00+02:00" } },
    ],
    source: "model",
  },
};

describe("InsightCard", () => {
  it("shows what the assistant noticed, why it matters and the emails it is based on", () => {
    renderWithProviders(<InsightCard proposal={insight} />);

    const card = screen.getByRole("article", { name: "StreamBox got more expensive" });
    expect(card).toHaveTextContent("The October invoice is 14.99 EUR");
    expect(card).toHaveTextContent("A 50 % increase");
    expect(within(card).getByText("High")).toBeInTheDocument();
    const sources = within(card).getAllByRole("link", { name: /source email/i });
    expect(sources.map((link) => link.getAttribute("href"))).toEqual(["/inbox?email=12", "/inbox?email=10"]);
  });

  it("keeps only the follow-ups left ticked", async () => {
    const user = userEvent.setup();
    const calls = mockApi({ "POST /api/proposals/31/decision": { body: { ...insight, status: "EDITED_AND_APPROVED" } } });
    renderWithProviders(<InsightCard proposal={insight} />);

    await user.click(screen.getByRole("checkbox", { name: "Call StreamBox" }));
    await user.click(screen.getByRole("button", { name: "Keep StreamBox got more expensive" }));

    await waitFor(() => expect(calls.some((call) => call.path === "/api/proposals/31/decision")).toBe(true));
    const decision = calls.find((call) => call.path === "/api/proposals/31/decision")?.body as {
      decision: string;
      payload: { followUps: { title: string }[] };
    };
    expect(decision.decision).toBe("APPROVE");
    expect(decision.payload.followUps.map((followUp) => followUp.title)).toEqual(["Check the StreamBox plan"]);
  });

  it("dismisses an insight", async () => {
    const user = userEvent.setup();
    const calls = mockApi({ "POST /api/proposals/31/decision": { body: { ...insight, status: "REJECTED" } } });
    renderWithProviders(<InsightCard proposal={insight} />);

    await user.click(screen.getByRole("button", { name: "Dismiss StreamBox got more expensive" }));

    await waitFor(() => expect(calls.find((call) => call.path === "/api/proposals/31/decision")?.body).toEqual({ decision: "REJECT" }));
  });
});
