/** A point in time with the number of emails processed by then. */
export interface ProgressSample {
  at: number;
  processed: number;
}

/** Observed seconds per email between two samples; null until at least one email finished in between. */
export function secondsPerEmail(first: ProgressSample, latest: ProgressSample): number | null {
  const emails = latest.processed - first.processed;
  const seconds = (latest.at - first.at) / 1000;
  if (emails <= 0 || seconds <= 0) return null;
  return seconds / emails;
}

/** "less than a minute", "about 15 min", "about 2 h 10 min". */
export function formatDuration(seconds: number): string {
  if (seconds < 60) return "less than a minute";
  const minutes = Math.round(seconds / 60);
  if (minutes < 60) return `about ${minutes} min`;
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  return rest === 0 ? `about ${hours} h` : `about ${hours} h ${rest} min`;
}
