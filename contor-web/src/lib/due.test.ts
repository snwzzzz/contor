import { describe, expect, it } from "vitest";
import { describeDue } from "./due";

const today = new Date(2026, 9, 8, 15, 30);

describe("describeDue", () => {
  it("says how much time is left", () => {
    expect(describeDue("2026-10-08", today)).toEqual({ label: "Due today", tone: "today" });
    expect(describeDue("2026-10-09", today)).toEqual({ label: "Due tomorrow", tone: "soon" });
    expect(describeDue("2026-10-12", today)).toEqual({ label: "Due in 4 days", tone: "soon" });
    expect(describeDue("2026-10-30", today)).toEqual({ label: "Due 30 Oct", tone: "later" });
    expect(describeDue("2027-01-03", today)).toEqual({ label: "Due 3 Jan 2027", tone: "later" });
  });

  it("says how late an overdue to-do is", () => {
    expect(describeDue("2026-10-07", today)).toEqual({ label: "Overdue by 1 day", tone: "overdue" });
    expect(describeDue("2026-10-03", today)).toEqual({ label: "Overdue by 5 days", tone: "overdue" });
    expect(describeDue("2026-08-01", today)).toEqual({ label: "Overdue since 1 Aug", tone: "overdue" });
  });

  it("has nothing to say without a valid date", () => {
    expect(describeDue(null, today)).toBeNull();
    expect(describeDue("soon", today)).toBeNull();
  });
});
