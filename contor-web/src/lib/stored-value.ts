import { useCallback, useSyncExternalStore } from "react";

/**
 * Per-viewer UI preferences in localStorage (sidebar width, collection order). Never for data that must persist:
 * storage can be empty or blocked, so every read falls back and every write is best effort.
 */
const CHANGE_EVENT = "contor:stored-value";

function readRaw(key: string): string | null {
  try {
    return window.localStorage.getItem(key);
  } catch {
    return null;
  }
}

export function writeStoredValue(key: string, value: unknown): void {
  try {
    window.localStorage.setItem(key, JSON.stringify(value));
  } catch {
    // Blocked storage: the value just is not remembered.
  }
  window.dispatchEvent(new CustomEvent(CHANGE_EVENT, { detail: key }));
}

function subscribe(onChange: () => void) {
  window.addEventListener(CHANGE_EVENT, onChange);
  window.addEventListener("storage", onChange);
  return () => {
    window.removeEventListener(CHANGE_EVENT, onChange);
    window.removeEventListener("storage", onChange);
  };
}

/** Parsed values per key and raw string, so the snapshot stays referentially stable between renders. */
const cache = new Map<string, { raw: string | null; value: unknown }>();

/**
 * The stored value of `key`, or `fallback` when missing, unreadable or rejected by `parse`. Renders `fallback` on the
 * server and during hydration, then the stored value. Pass module-level `fallback` and `parse` (stable references).
 */
export function useStoredValue<T>(key: string, fallback: T, parse: (raw: unknown) => T | undefined): T {
  const getSnapshot = useCallback(() => {
    const raw = readRaw(key);
    const cached = cache.get(key);
    if (cached && cached.raw === raw) {
      return cached.value as T;
    }
    let value: T = fallback;
    if (raw !== null) {
      try {
        value = parse(JSON.parse(raw)) ?? fallback;
      } catch {
        value = fallback;
      }
    }
    cache.set(key, { raw, value });
    return value;
  }, [key, fallback, parse]);

  return useSyncExternalStore(subscribe, getSnapshot, () => fallback);
}
