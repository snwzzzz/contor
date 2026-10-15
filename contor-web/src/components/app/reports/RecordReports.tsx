"use client";

import { useQueryClient } from "@tanstack/react-query";
import { FileText, Link2, LoaderCircle, MessageSquare } from "lucide-react";
import Link from "next/link";
import { useGetAiStatus } from "@/lib/api/generated/endpoints/ai/ai";
import { getListRecordReportsQueryKey, useCreateReport, useListRecordReports } from "@/lib/api/generated/endpoints/reports/reports";
import type { ReportSummaryDto } from "@/lib/api/generated/model/reportSummaryDto";
import { isGenerating, REPORT_POLL_MS, REPORT_STEP, reportHref, sourcesLabel } from "@/lib/report";
import { ReportPrompt } from "./ReportPrompt";

const SUGGESTIONS = ["Summarize this record", "What went wrong?", "What went well?", "What is still open?"] as const;
const NOT_READY = "The AI model is not ready: start Ollama and download the models in Settings → AI.";
const DATE_FORMAT = new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" });

/**
 * The record's reports in its Details tab: a chat-style prompt that asks the assistant about the record, and the
 * answers so far (newest first) with their progress, first sentence and how many sentences have a source.
 */
export function RecordReports({ collectionId, recordId }: { collectionId: number; recordId: number }) {
  const queryClient = useQueryClient();
  const { data: status } = useGetAiStatus({ query: { retry: false } });
  const { data: reports } = useListRecordReports(collectionId, recordId, {
    query: {
      retry: false,
      refetchInterval: (query) => (query.state.data?.some((report) => isGenerating(report.state)) ? REPORT_POLL_MS : false),
    },
  });
  const create = useCreateReport({
    mutation: { onSuccess: () => queryClient.invalidateQueries({ queryKey: getListRecordReportsQueryKey(collectionId, recordId) }) },
  });
  const disabledReason = status !== undefined && !status.ready ? NOT_READY : undefined;

  return (
    <section aria-label="Reports" className="grid gap-3">
      <ReportPrompt
        label="Ask about this record"
        placeholder="Ask about this record, or describe the report you want…"
        submitLabel="Create report"
        suggestions={SUGGESTIONS}
        allowEmpty
        disabledReason={disabledReason}
        pending={create.isPending}
        onSubmit={(prompt) => create.mutate({ collectionId, recordId, data: prompt ? { prompt } : {} })}
      />
      {reports && reports.length > 0 ? (
        <ul aria-label="Reports on this record" className="grid gap-2">
          {reports.map((report) => (
            <li key={report.id}>
              <ReportCard collectionId={collectionId} recordId={recordId} report={report} />
            </li>
          ))}
        </ul>
      ) : null}
    </section>
  );
}

function ReportCard({ collectionId, recordId, report }: { collectionId: number; recordId: number; report: ReportSummaryDto }) {
  const generating = isGenerating(report.state);
  return (
    <Link
      href={reportHref(collectionId, recordId, report.id)}
      className="grid gap-1 rounded-xl border p-4 transition-colors hover:border-primary hover:bg-muted/40"
    >
      <span className="flex items-center gap-2 text-sm font-medium">
        <FileText className="size-4 shrink-0 text-primary" aria-hidden="true" />
        <span className="truncate">{report.title ?? report.prompt ?? "Report"}</span>
        <span className="ml-auto shrink-0 text-xs font-normal text-muted-foreground">{DATE_FORMAT.format(new Date(report.createdAt))}</span>
      </span>
      {report.prompt && report.title ? <span className="truncate text-xs text-muted-foreground">“{report.prompt}”</span> : null}
      {generating ? (
        <span className="flex items-center gap-1.5 text-sm text-muted-foreground">
          <LoaderCircle className="size-3.5 animate-spin" aria-hidden="true" />
          {REPORT_STEP[report.state]}
        </span>
      ) : report.state === "FAILED" ? (
        <span className="text-sm text-destructive">The report could not be generated.</span>
      ) : (
        <>
          {report.summary ? <span className="line-clamp-2 text-sm">{report.summary}</span> : null}
          <span className="flex flex-wrap items-center gap-3 text-xs text-muted-foreground">
            <span className="inline-flex items-center gap-1">
              <Link2 className="size-3.5" aria-hidden="true" />
              {sourcesLabel(report.claimsVerified, report.claimsTotal)}
            </span>
            {report.followUpCount > 0 ? (
              <span className="inline-flex items-center gap-1">
                <MessageSquare className="size-3.5" aria-hidden="true" />
                {report.followUpCount} {report.followUpCount === 1 ? "follow-up" : "follow-ups"}
              </span>
            ) : null}
          </span>
        </>
      )}
    </Link>
  );
}
