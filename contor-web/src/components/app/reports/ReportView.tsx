"use client";

import { useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, CircleAlert, Link2, LoaderCircle, RefreshCw, Trash2 } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { type ReactNode, useState } from "react";
import { AppButton } from "@/components/app/AppButton";
import { ErrorMessage } from "@/components/app/ErrorMessage";
import {
  getGetReportQueryKey,
  getListRecordReportsQueryKey,
  useAskReportQuestion,
  useDeleteReport,
  useGetReport,
  useRegenerateReport,
} from "@/lib/api/generated/endpoints/reports/reports";
import type { ReportDto } from "@/lib/api/generated/model/reportDto";
import { isGenerating, REPORT_POLL_MS, REPORT_STEP, reportHref, sourcesLabel } from "@/lib/report";
import { ReportBody } from "./ReportBody";
import { ReportPrompt } from "./ReportPrompt";
import { SourcePanel } from "./SourcePanel";

const DATE_FORMAT = new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit" });

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
 */
export function ReportView({ collectionId, recordId, reportId }: { collectionId: number; recordId: number; reportId: number }) {
  const router = useRouter();
  const queryClient = useQueryClient();
  const { data: report, error } = useGetReport(reportId, {
    query: { retry: false, refetchInterval: (query) => (anyGenerating(query.state.data) ? REPORT_POLL_MS : false) },
  });
  const [selection, setSelection] = useState<Selection>();
  const [confirmDelete, setConfirmDelete] = useState(false);
  const refresh = () => queryClient.invalidateQueries({ queryKey: getGetReportQueryKey(reportId) });
  const ask = useAskReportQuestion({ mutation: { onSuccess: refresh } });
  const regenerate = useRegenerateReport({
    mutation: {
      onSuccess: async (created) => {
        await queryClient.invalidateQueries({ queryKey: getListRecordReportsQueryKey(collectionId, recordId) });
        if (created.parentReportId === undefined) router.push(reportHref(collectionId, recordId, created.id));
        else await refresh();
      },
    },
  });
  const remove = useDeleteReport({
    mutation: {
      onSuccess: async () => {
        await queryClient.invalidateQueries({ queryKey: getListRecordReportsQueryKey(collectionId, recordId) });
        router.push(`/collections/${collectionId}/records/${recordId}`);
      },
    },
  });

  if (error) return <ErrorMessage message="This report does not exist (anymore)." />;
  if (!report) return <p className="text-sm text-muted-foreground">Loading…</p>;

  const busy = anyGenerating(report);
  const shown = selection ? [report, ...report.followUps].find((candidate) => candidate.id === selection.reportId) ?? report : report;
  const select = (owner: number) => (marker: number) =>
    setSelection((current) => (current?.reportId === owner && current.marker === marker ? undefined : { reportId: owner, marker }));

  return (
    <article className="mx-auto grid max-w-6xl gap-6">
      <Link href={`/collections/${collectionId}/records/${recordId}`}
        className="inline-flex w-fit items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground">
        <ArrowLeft className="size-4" aria-hidden="true" />
        Back to the record
      </Link>

      <header className="grid gap-2 border-b pb-5">
        <h1 className="text-2xl font-semibold tracking-tight">{report.title ?? "Report"}</h1>
        <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-muted-foreground">
          <span>{DATE_FORMAT.format(new Date(report.generatedAt ?? report.createdAt))}</span>
          <span>{report.records.length} {report.records.length === 1 ? "record" : "records"}</span>
          <span>{report.emailCount} {report.emailCount === 1 ? "email" : "emails"}</span>
          {report.state === "READY" ? (
            <span className="inline-flex items-center gap-1 font-medium">
              <Link2 className="size-3.5" aria-hidden="true" />
              {sourcesLabel(report.claimsVerified, report.claimsTotal)}
            </span>
          ) : null}
        </div>
        <div className="flex flex-wrap gap-2 pt-1">
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
        <div className="grid content-start gap-8">
          {report.prompt ? <Question>{report.prompt}</Question> : null}
          <Answer>
            <ReportState report={report} />
            <ReportBody report={report} selected={selection?.reportId === report.id ? selection.marker : undefined} onSelect={select(report.id)} />
          </Answer>
          {report.followUps.map((followUp) => (
            <section key={followUp.id} aria-labelledby={`follow-up-${followUp.id}`} className="grid gap-6 border-t pt-6">
              <Question>
                <h2 id={`follow-up-${followUp.id}`} className="font-medium">{followUp.prompt ?? "Follow-up"}</h2>
              </Question>
              <Answer>
                <ReportState report={followUp} />
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
  return <section aria-label="Assistant's answer" className="grid gap-8">{children}</section>;
}

/** Progress while the report is generated, the reason when it failed; nothing when it is ready. */
function ReportState({ report }: { report: ReportDto }) {
  if (isGenerating(report.state)) {
    return (
      <p role="status" className="flex items-center gap-2 rounded-xl border border-dashed p-4 text-sm text-muted-foreground">
        <LoaderCircle className="size-4 animate-spin" aria-hidden="true" />
        {REPORT_STEP[report.state]}
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
