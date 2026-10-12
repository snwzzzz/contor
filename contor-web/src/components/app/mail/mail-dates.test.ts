import { describe, expect, it } from "vitest";
import { dateGroup, groupByDate, listTime } from "./mail-dates";

// Saturday, 10 Oct 2026, 14:00 local time; the week starts on Monday 5 Oct.
const NOW = new Date(2026, 9, 10, 14, 0);
const at = (month: number, day: number, hour = 9, minute = 30, year = 2026) => new Date(year, month - 1, day, hour, minute).toISOString();

describe("mail-dates", () => {
  it("groups like Outlook: today, yesterday, this week, last week, this month, then by month", () => {
    expect(dateGroup(at(10, 10), NOW)).toBe("Today");
    expect(dateGroup(at(10, 9), NOW)).toBe("Yesterday");
    expect(dateGroup(at(10, 5), NOW)).toBe("This week");
    expect(dateGroup(at(10, 4), NOW)).toBe("Last week");
    expect(dateGroup(at(9, 28), NOW)).toBe("Last week");
    expect(dateGroup(at(10, 1), NOW)).toBe("Last week");
    expect(dateGroup(at(9, 27), NOW)).toBe("September 2026");
    expect(dateGroup(at(12, 31, 9, 0, 2025), NOW)).toBe("December 2025");
  });

  it("keeps the newest-first order and makes one group per label", () => {
    const groups = groupByDate([{ id: 1, receivedAt: at(10, 10) }, { id: 2, receivedAt: at(10, 10, 8) }, { id: 3, receivedAt: at(10, 6) }], NOW);
    expect(groups.map((group) => [group.label, group.items.map((item) => item.id)])).toEqual([["Today", [1, 2]], ["This week", [3]]]);
  });

  it("shows the time today, weekday and time this week, else the date", () => {
    expect(listTime(at(10, 10, 12, 46), NOW)).toBe("12:46");
    expect(listTime(at(10, 6, 21, 5), NOW)).toBe("Tue 21:05");
    expect(listTime(at(9, 7), NOW)).toBe("7 Sept");
    expect(listTime(at(12, 31, 9, 0, 2025), NOW)).toBe("31 Dec 2025");
  });
});
