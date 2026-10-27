export type TimeOfDay = "morning" | "afternoon" | "evening";

/** Morning 5–12, afternoon 12–18, evening otherwise (local time). */
export function timeOfDay(date: Date): TimeOfDay {
  const hour = date.getHours();
  if (hour >= 5 && hour < 12) return "morning";
  if (hour >= 12 && hour < 18) return "afternoon";
  return "evening";
}

/**
 * "Good morning, anna"; plain "Welcome back" while the time of day is not known yet (server render, hydration), without
 * the name: the client may already have the user cached when the server rendered without it.
 */
export function greeting(period: TimeOfDay | null, name?: string): string {
  if (!period) return "Welcome back";
  return name ? `Good ${period}, ${name}` : `Good ${period}`;
}
