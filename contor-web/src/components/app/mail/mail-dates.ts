/** Dates of the mail list the way Outlook shows them: headings per period and a short time per row. */

const TIME = new Intl.DateTimeFormat("en-GB", { hour: "2-digit", minute: "2-digit" });
const WEEKDAY = new Intl.DateTimeFormat("en-GB", { weekday: "short" });
const DAY_MONTH = new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "short" });
const DAY_MONTH_YEAR = new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "short", year: "numeric" });
const MONTH_YEAR = new Intl.DateTimeFormat("en-GB", { month: "long", year: "numeric" });

const DAY_MS = 24 * 60 * 60 * 1000;

function startOfDay(date: Date): Date {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate());
}

/** Monday of the week `date` is in (weeks start on Monday). */
function startOfWeek(date: Date): Date {
  const day = startOfDay(date);
  return new Date(day.getTime() - ((day.getDay() + 6) % 7) * DAY_MS);
}

/** Heading of the period an email falls in: Today, Yesterday, This week, Last week, Earlier this month, then "September 2026". */
export function dateGroup(receivedAt: string, now: Date): string {
  const date = new Date(receivedAt);
  const today = startOfDay(now);
  const week = startOfWeek(now);
  if (date >= today) return "Today";
  if (date >= new Date(today.getTime() - DAY_MS)) return "Yesterday";
  if (date >= week) return "This week";
  if (date >= new Date(week.getTime() - 7 * DAY_MS)) return "Last week";
  if (date.getFullYear() === now.getFullYear() && date.getMonth() === now.getMonth()) return "Earlier this month";
  return MONTH_YEAR.format(date);
}

/** Consecutive emails (newest first) under their period heading. */
export function groupByDate<T extends { receivedAt: string }>(items: readonly T[], now: Date): { label: string; items: T[] }[] {
  const groups: { label: string; items: T[] }[] = [];
  for (const item of items) {
    const label = dateGroup(item.receivedAt, now);
    const last = groups.at(-1);
    if (last?.label === label) last.items.push(item);
    else groups.push({ label, items: [item] });
  }
  return groups;
}

/** Time of a row: "12:46" today, "Tue 21:05" this week, else "7 Sept" (with the year when it is not this year). */
export function listTime(receivedAt: string, now: Date): string {
  const date = new Date(receivedAt);
  if (date >= startOfDay(now)) return TIME.format(date);
  if (date >= startOfWeek(now)) return `${WEEKDAY.format(date)} ${TIME.format(date)}`;
  return date.getFullYear() === now.getFullYear() ? DAY_MONTH.format(date) : DAY_MONTH_YEAR.format(date);
}
