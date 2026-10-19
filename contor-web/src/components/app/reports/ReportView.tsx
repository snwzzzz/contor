"use client";

import { useQueryClient } from "@tanstack/react-query";
import { CircleAlert, Link2, LoaderCircle, Maximize2, RefreshCw, Trash2 } from "lucide-react";
import { useRouter } from "next/navigation";
import { type ReactNode, useState } from "react";
import { AppButton } from "@/components/app/AppButton";
import { ErrorMessage } from "@/components/app/ErrorMessage";
import { CommandLink } from "@/components/app/PageCommands";
import { collectionTrail, PageTrail, type Crumb } from "@/components/app/PageHeader";
import { useGetCollection } from "@/lib/api/generated/endpoints/collections/collections";
import { useGetRecord } from "@/lib/api/generated/endpoints/records/records";
import {
  getGetReportQueryKey,
  getListCollectionReportsQueryKey,
  getListRecordReportsQueryKey,
  useAskReportQuestion,
  useDeleteReport,
  useGetReport,
  useRegenerateReport,
} from "@/lib/api/generated/endpoints/reports/reports";
import type { ReportDto } from "@/lib/api/generated/model/reportDto";
import { isGenerating, REPORT_POLL_MS, reportHref, reportStep, sourcesLabel } from "@/lib/report";
import { cn } from "@/lib/utils";
import { ReportBody } from "./ReportBody";
import { ReportPrompt } from "./ReportPrompt";
import { SourcePanel } from "./SourcePanel";

const DATE_FORMAT = new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit" });

interface ReportViewProps {
  collectionId: number;
  recordId?: number;
  reportId: number;
  embedded?: boolean;
  /** Embedded only: the report to show after regenerating (its id) or deleting (null). */
  onNavigate?: (reportId: number | null) => void;
}

/** Which source is open: markers are numbered per report (a follow-up answer has its own). */
interface Selection {
  reportId: number;
  marker: number;
}

function anyGenerating(report: ReportDto | undefined): boolean {
  return report !== undefined && (isGenerating(report.state) || report.followUps.some((followUp) => isGenerating(followUp.state)));
}

/** Supporting quotes by marker, to highlight the quoted words inside their email paragraph. */
function quotesOf(report: ReportDto): Map<number, string> {
  const quotes = new Map<number, string>();
  for (const section of report.sections) {
    for (const finding of section.findings) {
      for (const quote of finding.quotes) {
        if (!quotes.has(quote.marker)) quotes.set(quote.marker, quote.text);
      }
    }
  }
  return quotes;
}

/**
 * One report as a page, read like a conversation: header with how many sentences have a source, then each question the
 * user asked (the request, then the follow-ups) as their own message bubble and the assistant's answer below it, its sections
 * with their source markers, and a follow-up prompt; next to it the sources panel, where a clicked
 * marker shows exactly what was cited (the field value, the email paragraph with the quote marked) and links there.
 * Without {@code recordId} it is a report over the whole collection: its scope is the table's filters and search.
 * {@code embedded}: shown inside the record's Reports tab (no breadcrumbs, an h2 title, "Open full screen"); regenerating
 * or deleting then reports the report to show next through {@code onNavigate} instead of changing the page.
 */
export function ReportView({ collectionId, recordId, reportId, embedded = false, onNavigate }: ReportViewProps) {
  const router = useRouter();
  const queryClient = useQueryClient();
  const { data: report, error } = useGetReport(reportId, {
    query: { retry: false, refetchInterval: (query) => (anyGenerating(query.state.data) ? REPORT_POLL_MS : false) },
  });
  const [selection, setSelection] = useState<Selection>();
  const [confirmDelete, setConfirmDelete] = useState(false);
  const refresh = () => queryClient.invalidateQueries({ queryKey: getGetReportQueryKey(reportId) });
  const collection = recordId === undefined;
  const home = collection ? `/collections/${collectionId}` : `/collections/${collectionId}/records/${recordId}`;
  // Names for the breadcrumbs (cached when coming from the collection or record page).
  const { data: collectionDto } = useGetCollection(collectionId, { query: { enabled: !embedded, retry: false } });
  const { data: recordDto } = useGetRecord(collectionId, recordId ?? 0, { query: { enabled: !embedded && !collection, retry: false } });
  const trail: Crumb[] = collectionTrail({ id: collectionId, slug: collectionDto?.slug, name: collectionDto?.name });
  if (!collection) trail.push({ label: recordDto?.title ?? "Record", href: home });
  const refreshList = () => queryClient.invalidateQueries({
    queryKey: collection ? getListCollectionReportsQueryKey(collectionId) : getListRecordReportsQueryKey(collectionId, recordId),
  });
  const ask = useAskReportQuestion({ mutation: { onSuccess: refresh } });
  const regenerate = useRegenerateReport({
    mutation: {
      onSuccess: async (created) => {
        await refreshList();
        if (created.parentReportId !== undefined) await refresh();
        else if (embedded) onNavigate?.(created.id);
        else router.push(reportHref(collectionId, recordId, created.id));
      },
    },
  });
  const remove = useDeleteReport({
    mutation: {
      onSuccess: async () => {
        await refreshList();
        if (embedded) onNavigate?.(null);
        else router.push(home);
      },
    },
  });

  if (error) return <ErrorMessage message="This report does not exist (anymore)." />;
  if (!report) return <p className="text-sm text-muted-foreground">Loading…</p>;

  const busy = anyGenerating(report);
  const shown = selection ? [report, ...report.followUps].find((candidate) => candidate.id === selection.reportId) ?? report : report;
  const select = (owner: number) => (marker: number) =>
    setSelection((current) => (current?.reportId === owner && current.marker === marker ? undefined : { reportId: owner, marker }));

  const Title = embedded ? "h2" : "h1";

  return (
    <article className={cn("grid gap-6", embedded ? "min-w-0" : "mx-auto max-w-6xl")}>
      {embedded ? null : <PageTrail trail={trail} current={report.title ?? "Report"} />}

      <header className="grid gap-2 border-b pb-5">
        <Title className={cn("font-semibold tracking-tight", embedded ? "text-xl" : "text-2xl")}>{report.title ?? "Report"}</Title>
        <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-muted-foreground">
          <span>{DATE_FORMAT.format(new Date(report.generatedAt ?? report.createdAt))}</span>
          {collection ? (
            <>
              <span>Whole collection</span>
              {(report.filters ?? []).map((filter) => (
                <code key={filter} className="rounded bg-muted px-1.5 py-0.5">{filter}</code>
              ))}
              {report.search ? <span>“{report.search}”</span> : null}
            </>
          ) : (
            <>
              <span>{report.records.length} {report.records.length === 1 ? "record" : "records"}</span>
              <span>{report.emailCount} {report.emailCount === 1 ? "email" : "emails"}</span>
            </>
          )}
          {report.state === "READY" ? (
            <span className="inline-flex items-center gap-1 font-medium">
              <Link2 className="size-3.5" aria-hidden="true" />
              {sourcesLabel(report.claimsVerified, report.claimsTotal)}
            </span>
          ) : null}
        </div>
        <div className="flex flex-wrap items-center gap-2 pt-1">
          {embedded ? <CommandLink href={reportHref(collectionId, recordId, report.id)} icon={Maximize2} label="Open full screen" /> : null}
          <AppButton variant="outline" size="sm" loading={regenerate.isPending} disabled={busy}
            onClick={() => regenerate.mutate({ reportId: report.id })}>
            <RefreshCw aria-hidden="true" />
            Regenerate
          </AppButton>
          {confirmDelete ? (
            <>
              <AppButton variant="destructive" size="sm" loading={remove.isPending} onClick={() => remove.mutate({ reportId: report.id })}>
                Delete report
              </AppButton>
              <AppButton variant="ghost" size="sm" onClick={() => setConfirmDelete(false)}>Cancel</AppButton>
            </>
          ) : (
            <AppButton variant="ghost" size="sm" onClick={() => setConfirmDelete(true)}>
              <Trash2 aria-hidden="true" />
              Delete
            </AppButton>
          )}
        </div>
      </header>

      <div className="grid gap-8 lg:grid-cols-[minmax(0,1fr)_20rem]">
        {/* minmax(0, 1fr): a wide table or a long word scrolls in its own box instead of widening the report column. */}
        <div className="grid min-w-0 grid-cols-[minmax(0,1fr)] content-start gap-8 break-words">
          {report.prompt ? <Question>{report.prompt}</Question> : null}
          <Answer>
            <ReportState report={report} collection={collection} />
            <ReportBody report={report} selected={selection?.reportId === report.id ? selection.marker : undefined} onSelect={select(report.id)} />
          </Answer>
          {report.followUps.map((followUp) => (
            <section key={followUp.id} aria-labelledby={`follow-up-${followUp.id}`} className="grid gap-6 border-t pt-6">
              <Question>
                <h2 id={`follow-up-${followUp.id}`} className="font-medium">{followUp.prompt ?? "Follow-up"}</h2>
              </Question>
              <Answer>
                <ReportState report={followUp} collection={collection} />
                <ReportBody report={followUp} selected={selection?.reportId === followUp.id ? selection.marker : undefined}
                  onSelect={select(followUp.id)} />
              </Answer>
            </section>
          ))}
          {/* Stays at the bottom of the screen while scrolling (-bottom-6 reaches past the scroll area's padding); the fade
              lets text pass under it. */}
          <div className="sticky -bottom-6 z-10 -mb-6 bg-linear-to-t from-background from-75% to-transparent pt-6 pb-6">
            <ReportPrompt
              label="Ask a follow-up"
              placeholder="Ask a follow-up question about this report…"
              submitLabel="Ask"
              disabledReason={busy ? "Wait until the report is ready." : report.state === "FAILED" ? "Regenerate the report first." : undefined}
              pending={ask.isPending}
              onSubmit={(question) => ask.mutate({ reportId: report.id, data: { question } })}
            />
          </div>
        </div>
        <SourcePanel
          sources={shown.sources}
          selected={selection?.reportId === shown.id ? selection.marker : undefined}
          quotes={quotesOf(shown)}
          onSelect={select(shown.id)}
        />
      </div>
    </article>
  );
}

/** What the user asked, as their own message on the right. */
function Question({ children }: { children: ReactNode }) {
  return (
    <section aria-label="Your question" className="flex justify-end">
      <div className="max-w-[85%] min-w-0 rounded-2xl rounded-tr-sm bg-primary px-4 py-2.5 text-sm break-words text-primary-foreground">
        {children}
      </div>
    </section>
  );
}

/** The assistant's answer, plain below the question (named for screen readers). */
function Answer({ children }: { children: ReactNode }) {
  return <section aria-label="Assistant's answer" className="grid min-w-0 grid-cols-[minmax(0,1fr)] gap-8">{children}</section>;
}

/** Progress while the report is generated, the reason when it failed; nothing when it is ready. */
function ReportState({ report, collection }: { report: ReportDto; collection: boolean }) {
  if (isGenerating(report.state)) {
    return (
      <p role="status" className="flex items-center gap-2 rounded-xl border border-dashed p-4 text-sm text-muted-foreground">
        <LoaderCircle className="size-4 animate-spin" aria-hidden="true" />
        {reportStep(report.state, collection)}
      </p>
    );
  }
  if (report.state === "FAILED") {
    return (
      <p role="alert" className="flex items-center gap-2 rounded-xl border border-destructive/50 bg-destructive/10 p-4 text-sm text-destructive">
        <CircleAlert className="size-4" aria-hidden="true" />
        {report.failureReason ?? "The report could not be generated."}
      </p>
    );
  }
  return null;
}
