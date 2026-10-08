export type DueTone = "overdue" | "today" | "soon" | "later";

export interface DueDescription {
  label: string;
  tone: DueTone;
}

const SOON_DAYS = 7;
const DAY_MONTH = new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "short" });
const DAY_MONTH_YEAR = new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "short", year: "numeric" });
const DATE = /^(\d{4})-(\d{2})-(\d{2})$/;

function plural(count: number, word: string): string {
  return `${count} ${word}${count === 1 ? "" : "s"}`;
}

/**
 * A deadline (YYYY-MM-DD) relative to `today`: "Due today", "Due in 4 days", "Overdue by 1 day", "Overdue since 1 Aug".
 * Null without a valid date.
 */
export function describeDue(due: string | null | undefined, today: Date): DueDescription | null {
  const match = due ? DATE.exec(due) : null;
  if (!match) return null;
  const date = new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3]));
  const start = new Date(today.getFullYear(), today.getMonth(), today.getDate());
  const days = Math.round((date.getTime() - start.getTime()) / 86_400_000);

  if (days < 0) {
    return -days <= SOON_DAYS
      ? { label: `Overdue by ${plural(-days, "day")}`, tone: "overdue" }
      : { label: `Overdue since ${(date.getFullYear() === start.getFullYear() ? DAY_MONTH : DAY_MONTH_YEAR).format(date)}`, tone: "overdue" };
  }
  if (days === 0) return { label: "Due today", tone: "today" };
  if (days === 1) return { label: "Due tomorrow", tone: "soon" };
  if (days <= SOON_DAYS) return { label: `Due in ${days} days`, tone: "soon" };
  const format = date.getFullYear() === start.getFullYear() ? DAY_MONTH : DAY_MONTH_YEAR;
  return { label: `Due ${format.format(date)}`, tone: "later" };
}
