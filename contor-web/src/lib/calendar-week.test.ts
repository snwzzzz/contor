import { describe, expect, it } from "vitest";
import { layoutDay, monthDays, monthLabel, startOfMonth, startOfWeek, weekDays, weekLabel } from "./calendar-week";

describe("calendar-week", () => {
  it("lays a month out as six full weeks starting on Monday", () => {
    expect(startOfMonth(new Date(2026, 9, 8, 15))).toEqual(new Date(2026, 9, 1));
    const days = monthDays(new Date(2026, 9, 1));
    expect(days).toHaveLength(42);
    expect(days[0]).toEqual(new Date(2026, 8, 28));
    expect(days[41]).toEqual(new Date(2026, 10, 8));
    expect(monthLabel(new Date(2026, 9, 1))).toBe("October 2026");
  });

  it("starts weeks on Monday", () => {
    expect(startOfWeek(new Date(2026, 9, 8, 15, 30))).toEqual(new Date(2026, 9, 5));
    expect(startOfWeek(new Date(2026, 9, 11))).toEqual(new Date(2026, 9, 5));
    expect(startOfWeek(new Date(2026, 9, 12))).toEqual(new Date(2026, 9, 12));
    expect(weekDays(new Date(2026, 9, 5)).map((day) => day.getDate())).toEqual([5, 6, 7, 8, 9, 10, 11]);
  });

  it("labels a week by its range, naming months and years only where they change", () => {
    expect(weekLabel(new Date(2026, 9, 5))).toBe("5 – 11 October 2026");
    expect(weekLabel(new Date(2026, 8, 28))).toBe("28 September – 4 October 2026");
    expect(weekLabel(new Date(2026, 11, 28))).toBe("28 December 2026 – 3 January 2027");
  });

  it("puts overlapping events side by side and lets the others take the full width", () => {
    const at = (hour: number, minute = 0) => new Date(2026, 9, 9, hour, minute);
    const placed = layoutDay([
      { id: 1, start: at(9), end: at(10) },
      { id: 2, start: at(9, 30), end: at(11) },
      { id: 3, start: at(10, 30), end: at(11, 30) },
      { id: 4, start: at(14), end: at(15) },
    ]);

    const byId = new Map(placed.map((entry) => [entry.id, entry]));
    // 1 and 2 overlap, 3 overlaps 2 but fits in 1's lane again: one cluster of two lanes.
    expect(byId.get(1)).toMatchObject({ lane: 0, lanes: 2 });
    expect(byId.get(2)).toMatchObject({ lane: 1, lanes: 2 });
    expect(byId.get(3)).toMatchObject({ lane: 0, lanes: 2 });
    expect(byId.get(4)).toMatchObject({ lane: 0, lanes: 1 });
  });
});
