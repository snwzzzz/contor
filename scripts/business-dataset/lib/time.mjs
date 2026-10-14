// Local wall-clock time in an IANA zone -> UTC instant (ISO, seconds, "Z").

function offsetMinutes(utcMillis, zone) {
  const parts = Object.fromEntries(new Intl.DateTimeFormat("en-US", {
    timeZone: zone, hourCycle: "h23", year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", second: "2-digit",
  }).formatToParts(new Date(utcMillis)).map((p) => [p.type, p.value]));
  const asUtc = Date.UTC(+parts.year, +parts.month - 1, +parts.day, +parts.hour, +parts.minute, +parts.second);
  return Math.round((asUtc - utcMillis) / 60000);
}

/** @param day YYYY-MM-DD, @param time HH:MM, @param zone e.g. Europe/Berlin */
export function toInstant(day, time, zone) {
  const [y, m, d] = day.split("-").map(Number);
  const [hh, mm] = time.split(":").map(Number);
  const naive = Date.UTC(y, m - 1, d, hh, mm);
  let utc = naive - offsetMinutes(naive, zone) * 60000;
  utc = naive - offsetMinutes(utc, zone) * 60000; // second pass settles DST edges
  return new Date(utc).toISOString().replace(".000Z", "Z");
}

export function isWeekend(day) {
  const dow = new Date(day + "T12:00:00Z").getUTCDay();
  return dow === 0 || dow === 6;
}
