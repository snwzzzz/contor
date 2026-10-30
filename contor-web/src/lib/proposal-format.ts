/** The parts of a proposal payload the UI shows (formats per kind are documented with the spec's ProposalAction). */
export interface ProposalPayload {
  collectionId?: number;
  recordId?: number;
  title?: string;
  name?: string;
  values?: Record<string, unknown>;
  /** CREATE_COLLECTION: `name`; MAP_API_ENDPOINT: `fieldSlug` filled from `path`. */
  fields?: { name?: string; fieldSlug?: string; path?: string }[];
  /** MAP_API_ENDPOINT: where an item's title is. */
  titlePath?: string;
}

const DATE_TIME = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}/;
const DATE = /^\d{4}-\d{2}-\d{2}$/;
const DATE_TIME_FORMAT = new Intl.DateTimeFormat("en-GB", { weekday: "short", day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" });
const DATE_FORMAT = new Intl.DateTimeFormat("en-GB", { weekday: "short", day: "numeric", month: "short", year: "numeric" });

export function proposalTitle(payload: ProposalPayload): string {
  return payload.title ?? payload.name ?? (payload.recordId != null ? `Record #${payload.recordId}` : "Proposal");
}

/** `full_name` → "Full name". */
export function humanize(slug: string): string {
  const words = slug.replaceAll("_", " ").trim();
  return words.charAt(0).toUpperCase() + words.slice(1);
}

/** A field value as text: dates and money formatted, booleans as Yes/No. */
export function formatValue(value: unknown): string {
  if (value === null || value === undefined) return "–";
  if (typeof value === "boolean") return value ? "Yes" : "No";
  if (typeof value === "object" && "amount" in value) {
    const money = value as { amount: unknown; currency?: unknown };
    const amount = Number(money.amount);
    const currency = typeof money.currency === "string" ? money.currency : undefined;
    if (Number.isFinite(amount) && currency) {
      try {
        return new Intl.NumberFormat("en-GB", { style: "currency", currency }).format(amount);
      } catch {
        // Unknown currency code: plain form below.
      }
    }
    return `${String(money.amount)} ${currency ?? ""}`.trim();
  }
  if (typeof value === "string") {
    if (DATE_TIME.test(value) && !Number.isNaN(Date.parse(value))) return DATE_TIME_FORMAT.format(new Date(value));
    if (DATE.test(value)) return DATE_FORMAT.format(new Date(`${value}T00:00:00`));
    return value;
  }
  return typeof value === "object" ? JSON.stringify(value) : String(value);
}
