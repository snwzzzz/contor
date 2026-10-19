"use client";

import { ArrowUpRight, Database, FileText, Mail, Network } from "lucide-react";
import Link from "next/link";
import { useGetEmailParagraphs } from "@/lib/api/generated/endpoints/mail/mail";
import type { CitationKind } from "@/lib/api/generated/model/citationKind";
import type { ReportSourceDto } from "@/lib/api/generated/model/reportSourceDto";
import { formatValue } from "@/lib/proposal-format";
import { highlightQuote, sourceHref } from "@/lib/report";
import { cn } from "@/lib/utils";

const ICON = { FIELD: Database, RECORD: FileText, RELATION: Network, EMAIL_PARAGRAPH: Mail } satisfies Record<CitationKind, unknown>;
const DATE_FORMAT = new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "short", year: "numeric" });

interface SourcePanelProps {
  sources: readonly ReportSourceDto[];
  selected: number | undefined;
  /** The supporting quote per marker, highlighted in its email paragraph. */
  quotes: ReadonlyMap<number, string>;
  onSelect: (marker: number) => void;
}

/** Every source of a report by marker; the selected one shows exactly what was cited, with a link to its place. */
export function SourcePanel({ sources, selected, quotes, onSelect }: SourcePanelProps) {
  return (
    // self-start: only as tall as its content (a stretched panel never sticks); nothing inside is clipped or scrolls.
    <aside aria-label="Sources" className="grid min-w-0 content-start gap-2 lg:sticky lg:top-4 lg:self-start">
      <h2 className="text-sm font-semibold">Sources</h2>
      {sources.length === 0 ? <p className="text-sm text-muted-foreground">No sources cited yet.</p> : null}
      <ol className="grid gap-1.5">
        {sources.map((source) => {
          const Icon = ICON[source.kind];
          const open = source.marker === selected;
          return (
            <li key={source.marker} className={cn("rounded-lg border", open ? "border-primary" : undefined)}>
              <button
                type="button"
                aria-expanded={open}
                onClick={() => onSelect(source.marker)}
                className="flex w-full items-start gap-2 px-3 py-2 text-left text-sm hover:bg-muted/50"
              >
                <span className="flex size-5 shrink-0 items-center justify-center rounded bg-muted text-xs tabular-nums">{source.marker}</span>
                <Icon className="mt-0.5 size-3.5 shrink-0 text-muted-foreground" aria-hidden="true" />
                <span className="min-w-0 break-words">{source.label}</span>
              </button>
              {open ? <SourceDetail source={source} quote={quotes.get(source.marker)} /> : null}
            </li>
          );
        })}
      </ol>
    </aside>
  );
}

function SourceDetail({ source, quote }: { source: ReportSourceDto; quote: string | undefined }) {
  const href = sourceHref(source);
  return (
    <div className="grid min-w-0 gap-2 border-t px-3 py-3 text-sm break-words">
      {source.sourceState === "CHANGED" ? (
        <p className="rounded-md bg-warning/15 px-2 py-1 text-xs text-warning">Changed since the report</p>
      ) : source.sourceState === "DELETED" ? (
        <p className="rounded-md bg-muted px-2 py-1 text-xs text-muted-foreground">No longer exists</p>
      ) : source.sourceState === "UNAVAILABLE" ? (
        <p className="text-xs text-muted-foreground">This email is in another user&apos;s mailbox.</p>
      ) : null}
      {source.kind === "EMAIL_PARAGRAPH" ? (
        source.sourceState === "UNAVAILABLE" ? null : (
          <>
            <p className="text-xs text-muted-foreground">
              {source.emailFrom ?? "Unknown sender"}
              {source.emailDate ? ` · ${DATE_FORMAT.format(new Date(source.emailDate))}` : null}
            </p>
            {source.emailId !== undefined ? (
              <EmailParagraphs emailId={source.emailId} index={source.paragraphIndex ?? 0} quote={quote ?? source.quote} />
            ) : (
              <p className="border-l-2 border-primary pl-2">{source.quote}</p>
            )}
          </>
        )
      ) : (
        <dl className="grid gap-0.5">
          {source.recordTitle ? <dd className="font-medium">{source.recordTitle}</dd> : null}
          {source.kind === "FIELD" ? (
            <>
              <dt className="text-xs text-muted-foreground">{source.fieldName ?? source.label}</dt>
              <dd>{formatValue(source.value)}</dd>
            </>
          ) : (
            <dt className="text-xs text-muted-foreground">{source.label}</dt>
          )}
        </dl>
      )}
      {href ? (
        <Link href={href} className="inline-flex items-center gap-1 text-xs font-medium text-primary underline-offset-4 hover:underline">
          {source.kind === "EMAIL_PARAGRAPH" ? "Open in inbox" : "Open record"}
          <ArrowUpRight className="size-3" aria-hidden="true" />
        </Link>
      ) : null}
    </div>
  );
}

/** The email's paragraphs around the cited one, which is highlighted with the quote marked inside it. */
function EmailParagraphs({ emailId, index, quote }: { emailId: number; index: number; quote: string | undefined }) {
  const { data: paragraphs, isPending } = useGetEmailParagraphs(emailId, { query: { retry: false } });
  if (isPending) return <p className="text-xs text-muted-foreground">Loading the email…</p>;
  const shown = (paragraphs ?? []).filter((paragraph) => Math.abs(paragraph.index - index) <= 1);
  if (shown.length === 0) return quote ? <p className="border-l-2 border-primary pl-2">{quote}</p> : null;
  return (
    <div className="grid gap-1.5">
      {shown.map((paragraph) => {
        if (paragraph.index !== index) {
          return <p key={paragraph.index} className="text-xs break-words text-muted-foreground whitespace-pre-line">{paragraph.text}</p>;
        }
        const split = highlightQuote(paragraph.text, quote);
        return (
          <p key={paragraph.index} data-testid="cited-paragraph" className="rounded-md bg-primary/10 px-2 py-1 break-words whitespace-pre-line">
            {split ? (
              <>
                {split.before}
                <mark className="rounded bg-primary/25 px-0.5 text-foreground">{split.match}</mark>
                {split.after}
              </>
            ) : paragraph.text}
          </p>
        );
      })}
    </div>
  );
}
