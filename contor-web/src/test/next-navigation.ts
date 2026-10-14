import { vi } from "vitest";

/**
 * Shared mock of next/navigation. In a test file:
 *   vi.mock("next/navigation", () => import("@/test/next-navigation"));
 * then inspect/reset `router` and set `navigationState.pathname`.
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

export function resetNavigation(pathname = "/inbox") {
  Object.values(router).forEach((fn) => fn.mockReset());
  navigationState.pathname = pathname;
  navigationState.search = "";
}

export const useRouter = () => router;
export const usePathname = () => navigationState.pathname;
export const useSearchParams = () => new URLSearchParams(navigationState.search);
export const redirect = vi.fn((url: string) => {
  throw new Error(`NEXT_REDIRECT ${url}`);
});
