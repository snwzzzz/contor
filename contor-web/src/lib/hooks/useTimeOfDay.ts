import { useSyncExternalStore } from "react";
import { timeOfDay, type TimeOfDay } from "@/lib/time-of-day";

const MINUTE_MS = 60_000;

function subscribe(onChange: () => void) {
  const timer = window.setInterval(onChange, MINUTE_MS);
  return () => window.clearInterval(timer);
}

function getSnapshot(): TimeOfDay {
  return timeOfDay(new Date());
}

/** Unknown on the server: the client's clock decides, so the server and hydration render agree. */
function getServerSnapshot(): TimeOfDay | null {
  return null;
}

/** The time of day on the user's clock; `null` during the server render and hydration. */
export function useTimeOfDay(): TimeOfDay | null {
  return useSyncExternalStore(subscribe, getSnapshot, getServerSnapshot);
}
