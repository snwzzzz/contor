/** Avatar helpers for mail senders and accounts. */

const INITIAL = /[\p{L}\p{N}]/u;

/** First letter or digit, upper-cased, for the sender avatar. */
export function initialOf(name: string): string {
  return INITIAL.exec(name)?.[0].toUpperCase() ?? "?";
}

/** Avatar tints (tokens --avatar-N); static class names so Tailwind generates them. */
export const AVATAR_TONES = [
  "bg-avatar-1 text-avatar-1-foreground",
  "bg-avatar-2 text-avatar-2-foreground",
  "bg-avatar-3 text-avatar-3-foreground",
  "bg-avatar-4 text-avatar-4-foreground",
  "bg-avatar-5 text-avatar-5-foreground",
] as const;

/** Same sender, same tint. */
export function avatarTone(name: string): (typeof AVATAR_TONES)[number] {
  let hash = 0;
  for (const char of name) hash = (hash * 31 + char.charCodeAt(0)) >>> 0;
  return AVATAR_TONES[hash % AVATAR_TONES.length];
}

/** Lower-cased domain of an email address (the part after the last @). */
export function domainOf(address: string | undefined): string | undefined {
  const at = address?.lastIndexOf("@") ?? -1;
  return address && at >= 0 ? address.slice(at + 1).toLowerCase() : undefined;
}
