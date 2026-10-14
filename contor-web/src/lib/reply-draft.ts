/** A recipient of a reply draft (DRAFT_REPLY payloads and kept drafts). */
export interface ReplyRecipient {
  name?: string | null;
  address?: string;
}

/** The DRAFT_REPLY payload (documented with the spec's ProposalAction). */
export interface ReplyDraftPayload {
  emailId: number;
  accountId?: number;
  conversationId?: string | null;
  to: ReplyRecipient[];
  cc: ReplyRecipient[];
  subject: string;
  body: string;
  language?: string | null;
  placeholders?: string[];
  /** What the reply does (SCHEDULE, ANSWER_QUESTIONS, …), since reply-v2. */
  intent?: string;
  /** The questions of the email and whether the draft answers them. */
  questions?: ReplyQuestion[];
  /** For an optional polite reply (intent COURTESY): REJECTION_THANKS, THANK_YOU, … */
  courtesyKind?: string;
  /** Free times from the calendar the draft offers. */
  proposedSlots?: ReplySlot[];
}

/** A question of the email: answered by the draft, left as a placeholder (false), or not reported (null). */
export interface ReplyQuestion {
  question: string;
  answered?: boolean | null;
}

/** An offered meeting time, ISO-8601 with offset. */
export interface ReplySlot {
  start: string;
  end: string;
}

/** The text of a draft the user edits (proposal payload or kept draft). */
export interface ReplyText {
  to: ReplyRecipient[];
  cc: ReplyRecipient[];
  subject: string;
  body: string;
}

/** `[available times]`: something the assistant left for the user to fill in (same rule as the backend). */
const PLACEHOLDER = /\[[^[\]\n]{1,80}\]/g;

/** Mail apps and browsers cut long `mailto:` links; longer drafts are only offered for copying. */
export const MAILTO_MAX_LENGTH = 2000;

/** The placeholders left in a text, each once, in order of appearance. */
export function placeholdersIn(text: string): string[] {
  return [...new Set(text.match(PLACEHOLDER) ?? [])];
}

/** The text in parts, placeholders marked, for highlighting. */
export function splitPlaceholders(text: string): { text: string; placeholder: boolean }[] {
  const parts: { text: string; placeholder: boolean }[] = [];
  let last = 0;
  for (const match of text.matchAll(PLACEHOLDER)) {
    if (match.index > last) parts.push({ text: text.slice(last, match.index), placeholder: false });
    parts.push({ text: match[0], placeholder: true });
    last = match.index + match[0].length;
  }
  if (last < text.length) parts.push({ text: text.slice(last), placeholder: false });
  return parts;
}

/** "Anna Berger <anna@acme.com>", or just the address. */
export function recipientLabel(recipient: ReplyRecipient): string {
  return recipient.name ? `${recipient.name} <${recipient.address ?? ""}>` : (recipient.address ?? "");
}

function encode(value: string): string {
  return encodeURIComponent(value).replaceAll("%40", "@");
}

/**
 * A `mailto:` link that opens the draft in the user's mail app (RFC 6068: CRLF line breaks, percent-encoded), or
 * `null` when it would be longer than {@link MAILTO_MAX_LENGTH} (then the draft can only be copied). Built in the
 * browser from the user's own draft; nothing goes into an app URL.
 */
export function mailtoLink(draft: ReplyText): string | null {
  const to = draft.to.flatMap((recipient) => (recipient.address ? [encode(recipient.address)] : [])).join(",");
  const cc = draft.cc.flatMap((recipient) => (recipient.address ? [encode(recipient.address)] : [])).join(",");
  const query = [
    cc ? `cc=${cc}` : null,
    `subject=${encode(draft.subject)}`,
    `body=${encode(draft.body.replace(/\r?\n/g, "\r\n"))}`,
  ].filter((part): part is string => part !== null);
  const link = `mailto:${to}?${query.join("&")}`;
  return link.length <= MAILTO_MAX_LENGTH ? link : null;
}

/** The draft as plain text for the clipboard: recipients, subject, then the body. */
export function draftAsText(draft: ReplyText): string {
  const lines = [`To: ${draft.to.map(recipientLabel).join(", ")}`];
  if (draft.cc.length > 0) lines.push(`Cc: ${draft.cc.map(recipientLabel).join(", ")}`);
  lines.push(`Subject: ${draft.subject}`, "", draft.body);
  return lines.join("\n");
}
