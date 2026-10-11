/** Avatar helpers for mail senders and accounts. */

import type { EmailSearchField } from "@/lib/api/generated/model/emailSearchField";

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

/** The searchable parts of an email summary. */
interface SearchableEmail {
  subject?: string;
  fromName?: string;
  fromAddress?: string;
  snippet?: string;
}

/**
 * Client-side counterpart of the inbox search for lists that are already loaded (a record's emails): `text` in the
 * parts `field` names. Summaries carry no recipients or body, so PEOPLE is the sender and CONTENT the preview text.
 */
export function matchesEmail(email: SearchableEmail, text: string, field: EmailSearchField): boolean {
  const needle = text.trim().toLowerCase();
  if (!needle) return true;
  const parts =
    field === "SUBJECT" ? [email.subject]
    : field === "PEOPLE" ? [email.fromName, email.fromAddress]
    : field === "CONTENT" ? [email.snippet]
    : [email.subject, email.fromName, email.fromAddress, email.snippet];
  return parts.some((part) => part?.toLowerCase().includes(needle));
}

/** First line of the quoted history of a reply: "On … wrote:", "Am … schrieb …:", "-----Original Message-----", "> …". */
const QUOTE_START = [
  /^On\b.*\bwrote:\s*$/i,
  /^Am\b.*\bschrieb\b.*:\s*$/i,
  /^-{2,}\s*(Original Message|Ursprüngliche Nachricht|Forwarded message)\s*-{2,}\s*$/i,
  /^>/,
];

/**
 * Splits a plain-text reply into what the sender wrote and the quoted history below it (`null` when there is none, or
 * when nothing but quotes is left): the history can then be folded away.
 */
export function splitQuoted(text: string): { fresh: string; quoted: string | null } {
  const lines = text.split("\n");
  const start = lines.findIndex((line) => QUOTE_START.some((pattern) => pattern.test(line.trim())));
  if (start <= 0) return { fresh: text, quoted: null };
  const fresh = lines.slice(0, start).join("\n").trimEnd();
  if (fresh.trim() === "") return { fresh: text, quoted: null };
  return { fresh, quoted: lines.slice(start).join("\n") };
}
