import { useSyncExternalStore } from "react";
import { vi } from "vitest";

/**
 * Shared mock of next/navigation. In a test file:
 *   vi.mock("next/navigation", () => import("@/test/next-navigation"));
 * then inspect/reset `router` and set `navigationState.pathname`. By default `router.push`/`replace` only record their
 * calls; `followNavigation()` makes them change the mocked URL too, so components reading `usePathname`/`useSearchParams`
 * re-render with it (for state that lives in the URL).
 */
export const router = {
  push: vi.fn(),
  replace: vi.fn(),
  refresh: vi.fn(),
  back: vi.fn(),
  forward: vi.fn(),
  prefetch: vi.fn(),
};

export const navigationState = { pathname: "/inbox", search: "" };

const listeners = new Set<() => void>();
// A new snapshot object per change, so useSyncExternalStore sees it.
let snapshot = { ...navigationState };

function notify() {
  snapshot = { ...navigationState };
  listeners.forEach((listener) => listener());
}

function subscribe(listener: () => void) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

/** Tests also set `navigationState` directly: the snapshot follows it, staying the same object while nothing changed. */
function getSnapshot() {
  if (snapshot.pathname !== navigationState.pathname || snapshot.search !== navigationState.search) {
    snapshot = { ...navigationState };
  }
  return snapshot;
}

function useLocation() {
  return useSyncExternalStore(subscribe, getSnapshot, getSnapshot);
}

export function resetNavigation(pathname = "/inbox") {
  Object.values(router).forEach((fn) => fn.mockReset());
  navigationState.pathname = pathname;
  navigationState.search = "";
  notify();
}

/** `router.push` and `router.replace` move the mocked URL (`/path?query` or `?query` on the current path). */
export function followNavigation() {
  const go = (href: string) => {
    const url = new URL(href, `http://localhost${navigationState.pathname}`);
    navigationState.pathname = url.pathname;
    navigationState.search = url.search.replace(/^\?/, "");
    notify();
  };
  router.push.mockImplementation(go);
  router.replace.mockImplementation(go);
}

export const useRouter = () => router;
export const usePathname = () => useLocation().pathname;
export const useSearchParams = () => new URLSearchParams(useLocation().search);
export const redirect = vi.fn((url: string) => {
  throw new Error(`NEXT_REDIRECT ${url}`);
});
