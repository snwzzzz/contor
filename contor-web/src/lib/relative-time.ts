const FORMAT = new Intl.RelativeTimeFormat("en", { numeric: "auto", style: "short" });

const UNITS: readonly [Intl.RelativeTimeFormatUnit, number][] = [
  ["day", 86_400_000],
  ["hour", 3_600_000],
  ["minute", 60_000],
];

/** How long ago `iso` was, in the largest fitting unit: "just now", "5 min. ago", "3 hr. ago", "yesterday", "5 days ago". */
export function relativeTime(iso: string, now: Date): string {
  const elapsed = now.getTime() - new Date(iso).getTime();
  for (const [unit, ms] of UNITS) {
    if (Math.abs(elapsed) >= ms) return FORMAT.format(-Math.round(elapsed / ms), unit);
  }
  return "just now";
}
