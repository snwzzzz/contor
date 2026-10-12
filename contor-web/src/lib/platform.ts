import { useSyncExternalStore } from "react";

/** macOS and iOS use ⌘ where other systems use Ctrl. */
export function isApplePlatform(platform: string): boolean {
  return /mac|iphone|ipad|ipod/i.test(platform);
}

/** "⌘K" on Apple systems, "Ctrl K" elsewhere. */
export function shortcutLabel(key: string, apple: boolean): string {
  return apple ? `⌘${key}` : `Ctrl ${key}`;
}

function currentPlatform(): string {
  const data = (navigator as Navigator & { userAgentData?: { platform?: string } }).userAgentData;
  return data?.platform || navigator.platform || "";
}

function subscribe(): () => void {
  return () => {};
}

/** Whether the browser runs on an Apple system. Server render and hydration assume not (Ctrl), then the real value. */
export function useIsApplePlatform(): boolean {
  return useSyncExternalStore(subscribe, () => isApplePlatform(currentPlatform()), () => false);
}
