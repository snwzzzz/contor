"use client";

import { useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, CircleAlert, Link2, LoaderCircle, RefreshCw, Trash2 } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { AppButton } from "@/components/app/AppButton";
import { ErrorMessage } from "@/components/app/ErrorMessage";
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
 * One report as a page: header with the request and how many sentences have a source, the sections with their source
 * markers, the follow-up answers and a follow-up prompt; next to it the sources panel, where a clicked
 * marker shows exactly what was cited (the field value, the email paragraph with the quote marked) and links there.
 * Without {@code recordId} it is a report over the whole collection: its scope is the table's filters and search.
 */
export function ReportView({ collectionId, recordId, reportId }: { collectionId: number; recordId?: number; reportId: number }) {
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
  const refreshList = () => queryClient.invalidateQueries({
    queryKey: collection ? getListCollectionReportsQueryKey(collectionId) : getListRecordReportsQueryKey(collectionId, recordId),
  });
  const ask = useAskReportQuestion({ mutation: { onSuccess: refresh } });
  const regenerate = useRegenerateReport({
    mutation: {
      onSuccess: async (created) => {
        await refreshList();
        if (created.parentReportId === undefined) router.push(reportHref(collectionId, recordId, created.id));
        else await refresh();
      },
    },
  });
  const remove = useDeleteReport({
    mutation: {
      onSuccess: async () => {
        await refreshList();
        router.push(home);
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
      <Link href={home} className="inline-flex w-fit items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground">
        <ArrowLeft className="size-4" aria-hidden="true" />
        {collection ? "Back to the collection" : "Back to the record"}
      </Link>

      <header className="grid gap-2 border-b pb-5">
        <h1 className="text-2xl font-semibold tracking-tight">{report.title ?? "Report"}</h1>
        {report.prompt ? <p className="text-sm text-muted-foreground">{report.prompt}</p> : null}
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
          <ReportState report={report} collection={collection} />
          <ReportBody report={report} selected={selection?.reportId === report.id ? selection.marker : undefined} onSelect={select(report.id)} />
          {report.followUps.map((followUp) => (
            <section key={followUp.id} aria-labelledby={`follow-up-${followUp.id}`} className="grid gap-4 border-t pt-6">
              <h2 id={`follow-up-${followUp.id}`} className="text-base font-semibold">{followUp.prompt ?? "Follow-up"}</h2>
              <ReportState report={followUp} collection={collection} />
              <ReportBody report={followUp} selected={selection?.reportId === followUp.id ? selection.marker : undefined}
                onSelect={select(followUp.id)} />
            </section>
          ))}
          <ReportPrompt
            label="Ask a follow-up"
            placeholder="Ask a follow-up question about this report…"
            submitLabel="Ask"
            disabledReason={busy ? "Wait until the report is ready." : report.state === "FAILED" ? "Regenerate the report first." : undefined}
            pending={ask.isPending}
            onSubmit={(question) => ask.mutate({ reportId: report.id, data: { question } })}
          />
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
