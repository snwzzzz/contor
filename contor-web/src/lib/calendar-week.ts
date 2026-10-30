/** Week math and event placement for the calendar's week view (local time, weeks start on Monday). */

const DAY_MONTH = new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "long" });
const DAY_MONTH_YEAR = new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "long", year: "numeric" });

export function addDays(date: Date, days: number): Date {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate() + days);
}

export function startOfDay(date: Date): Date {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate());
}

/** Monday 00:00 of the week `date` is in. */
export function startOfWeek(date: Date): Date {
  return addDays(startOfDay(date), -((date.getDay() + 6) % 7));
}

export function weekDays(start: Date): Date[] {
  return Array.from({ length: 7 }, (_, index) => addDays(start, index));
}

export function sameDay(a: Date, b: Date): boolean {
  return a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate();
}

/** "5 – 11 October 2026", "28 September – 4 October 2026", "28 December 2026 – 3 January 2027". */
export function weekLabel(start: Date): string {
  const end = addDays(start, 6);
  if (start.getFullYear() !== end.getFullYear()) return `${DAY_MONTH_YEAR.format(start)} – ${DAY_MONTH_YEAR.format(end)}`;
  if (start.getMonth() !== end.getMonth()) return `${DAY_MONTH.format(start)} – ${DAY_MONTH_YEAR.format(end)}`;
  return `${start.getDate()} – ${DAY_MONTH_YEAR.format(end)}`;
}

const MONTH_YEAR = new Intl.DateTimeFormat("en-GB", { month: "long", year: "numeric" });
const MONTH_GRID_DAYS = 42;

export function startOfMonth(date: Date): Date {
  return new Date(date.getFullYear(), date.getMonth(), 1);
}

/** The month grid: six full weeks from the Monday on or before the 1st, so every month has the same height. */
export function monthDays(month: Date): Date[] {
  const first = startOfWeek(startOfMonth(month));
  return Array.from({ length: MONTH_GRID_DAYS }, (_, index) => addDays(first, index));
}

/** "October 2026". */
export function monthLabel(month: Date): string {
  return MONTH_YEAR.format(month);
}

/** Minutes since midnight. */
export function minutesOf(date: Date): number {
  return date.getHours() * 60 + date.getMinutes();
}

export interface TimedEvent {
  id: number;
  start: Date;
  end: Date;
}

export interface PlacedEvent extends TimedEvent {
  /** Column inside its group of overlapping events, from 0. */
  lane: number;
  /** How many columns that group has. */
  lanes: number;
}

/**
 * Side-by-side columns for overlapping events of one day: events that overlap (directly or through a chain) form a
 * group; each takes the first column that is free at its start, and the group's width is split by its column count.
 */
export function layoutDay(events: readonly TimedEvent[]): PlacedEvent[] {
  const sorted = [...events].sort((a, b) => a.start.getTime() - b.start.getTime() || b.end.getTime() - a.end.getTime());
  const placed: PlacedEvent[] = [];
  let group: PlacedEvent[] = [];
  let laneEnds: number[] = [];
  let groupEnd = -Infinity;

  const closeGroup = () => {
    for (const entry of group) entry.lanes = laneEnds.length;
    placed.push(...group);
    group = [];
    laneEnds = [];
  };

  for (const event of sorted) {
    const start = event.start.getTime();
    if (start >= groupEnd) closeGroup();
    let lane = laneEnds.findIndex((end) => end <= start);
    if (lane === -1) lane = laneEnds.length;
    laneEnds[lane] = event.end.getTime();
    groupEnd = Math.max(groupEnd, event.end.getTime());
    group.push({ ...event, lane, lanes: 0 });
  }
  closeGroup();
  return placed;
}
