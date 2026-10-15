import type { ReportSourceDto } from "@/lib/api/generated/model/reportSourceDto";
import type { ReportState } from "@/lib/api/generated/model/reportState";

/** How often a report that is still being generated is fetched again. */
export const REPORT_POLL_MS = 2000;

const GENERATING: ReadonlySet<ReportState> = new Set<ReportState>(["QUEUED", "COLLECTING", "DRAFTING", "FACT_CHECKING"]);

/** What the assistant is doing in each state, for the progress line. */
export const REPORT_STEP: Record<ReportState, string> = {
  QUEUED: "Waiting for the assistant…",
  COLLECTING: "Collecting the record, its connections and emails…",
  DRAFTING: "Writing the report…",
  FACT_CHECKING: "Linking the sources…",
  READY: "Ready",
  FAILED: "Failed",
};

export function isGenerating(state: ReportState): boolean {
  return GENERATING.has(state);
}

export function reportHref(collectionId: number, recordId: number, reportId: number): string {
  return `/collections/${collectionId}/records/${recordId}/reports/${reportId}`;
}

/** The exact place of a source in contor; none for sources that are gone or in another user's mailbox. */
export function sourceHref(source: ReportSourceDto): string | undefined {
  if (source.sourceState === "DELETED" || source.sourceState === "UNAVAILABLE") return undefined;
  const record = source.collectionId !== undefined && source.recordId !== undefined
    ? `/collections/${source.collectionId}/records/${source.recordId}`
    : undefined;
  switch (source.kind) {
    case "FIELD":
      return record && source.fieldSlug ? `${record}?tab=details&field=${encodeURIComponent(source.fieldSlug)}` : record;
    case "RELATION":
      return record && source.relationId !== undefined ? `${record}?tab=connections&relation=${source.relationId}` : record;
    case "EMAIL_PARAGRAPH":
      return source.emailId !== undefined ? `/inbox?email=${source.emailId}&paragraph=${source.paragraphIndex ?? 0}` : undefined;
    default:
      return record;
  }
}

/** How many of the report's sentences point at a source (opinions and advice may have none). */
export function sourcesLabel(withSource: number, total: number): string {
  if (total === 0) return "No sentences";
  return `${withSource} of ${total} ${total === 1 ? "sentence" : "sentences"} with a source`;
}

export interface QuoteSplit {
  before: string;
  match: string;
  after: string;
}

/**
 * The quote's place in its paragraph (case and runs of whitespace ignored), split for highlighting; undefined when it
 * is not there.
 */
export function highlightQuote(paragraph: string, quote: string | undefined): QuoteSplit | undefined {
  const words = quote?.trim().split(/\s+/).filter(Boolean) ?? [];
  if (words.length === 0) return undefined;
  const pattern = new RegExp(words.map((word) => word.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")).join("\\s+"), "i");
  const found = pattern.exec(paragraph);
  if (!found) return undefined;
  return {
    before: paragraph.slice(0, found.index),
    match: found[0],
    after: paragraph.slice(found.index + found[0].length),
  };
}

export interface InlineMark {
  text: string;
  mark?: "strong" | "em" | "code";
}

const INLINE = /\*\*([^*]+?)\*\*|\*([^*\s][^*]*?)\*|`([^`]+)`/g;

/** A sentence's Markdown emphasis (**bold**, *italic*, `code`) as plain runs; anything else stays text. */
export function inlineMarks(text: string): InlineMark[] {
  const runs: InlineMark[] = [];
  let last = 0;
  for (const found of text.matchAll(INLINE)) {
    if (found.index > last) runs.push({ text: text.slice(last, found.index) });
    if (found[1] !== undefined) runs.push({ text: found[1], mark: "strong" });
    else if (found[2] !== undefined) runs.push({ text: found[2], mark: "em" });
    else if (found[3] !== undefined) runs.push({ text: found[3], mark: "code" });
    last = found.index + found[0].length;
  }
  if (last < text.length) runs.push({ text: text.slice(last) });
  return runs;
}
