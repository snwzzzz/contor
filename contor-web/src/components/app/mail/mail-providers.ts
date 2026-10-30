import type { MailProviderKind } from "@/lib/api/generated/model/mailProviderKind";

const LABELS: Record<MailProviderKind, string> = {
  MICROSOFT_GRAPH: "Outlook",
  GMAIL: "Gmail",
  DEMO: "Demo",
};

/** Product name of a mailbox's provider, shown next to its address (plain text, no brand colors). */
export function providerLabel(kind: MailProviderKind): string {
  return LABELS[kind];
}
