import { useStoredValue, writeStoredValue } from "@/lib/stored-value";

/** How times of day are shown: 24-hour ("14:30") or 12-hour with AM/PM ("2:30 PM"). A per-browser preference. */
export type TimeFormat = "24h" | "12h";

export const TIME_FORMAT_KEY = "contor.timeFormat";
const DEFAULT_FORMAT: TimeFormat = "24h";
const parseFormat = (raw: unknown): TimeFormat | undefined => (raw === "24h" || raw === "12h" ? raw : undefined);

/** The chosen time format; 24-hour on the server, during hydration and when nothing is stored. */
export function useTimeFormat(): TimeFormat {
  return useStoredValue(TIME_FORMAT_KEY, DEFAULT_FORMAT, parseFormat);
}

export function setTimeFormat(format: TimeFormat): void {
  writeStoredValue(TIME_FORMAT_KEY, format);
}

/** "14:05" (a time of day as HH:MM) in `format`: "14:05" or "2:05 PM"; empty stays empty. */
export function formatClock(clock: string, format: TimeFormat): string {
  if (!clock || format === "24h") return clock;
  const [hours, minutes] = clock.split(":");
  const hour = Number(hours);
  return `${hour % 12 === 0 ? 12 : hour % 12}:${minutes} ${hour < 12 ? "AM" : "PM"}`;
}

/** The local time of day of `date` in `format`. */
export function formatTime(date: Date, format: TimeFormat): string {
  return formatClock(`${String(date.getHours()).padStart(2, "0")}:${String(date.getMinutes()).padStart(2, "0")}`, format);
}

// "14:30", "1430", "9", "9.15", "2pm", "2:30 PM", "12 a.m."
const CLOCK = /^(\d{1,2})(?:[:.h]?(\d{2}))?\s*(?:([ap])\.?m?\.?)?$/i;

/** A typed time in either format as HH:MM, or null when it is not a time of day. */
export function parseClock(text: string): string | null {
  const match = CLOCK.exec(text.trim());
  if (!match) return null;
  let hour = Number(match[1]);
  const minute = match[2] === undefined ? 0 : Number(match[2]);
  const meridiem = match[3]?.toLowerCase();
  if (minute > 59) return null;
  if (meridiem) {
    if (hour < 1 || hour > 12) return null;
    hour = (hour % 12) + (meridiem === "p" ? 12 : 0);
  } else if (hour > 23) {
    return null;
  }
  return `${String(hour).padStart(2, "0")}:${String(minute).padStart(2, "0")}`;
}
