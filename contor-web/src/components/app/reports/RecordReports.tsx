"use client";

import { useQueryClient } from "@tanstack/react-query";
import { LoaderCircle } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useGetAiStatus } from "@/lib/api/generated/endpoints/ai/ai";
import { getListRecordReportsQueryKey, useCreateReport, useListRecordReports } from "@/lib/api/generated/endpoints/reports/reports";
import type { ReportSummaryDto } from "@/lib/api/generated/model/reportSummaryDto";
import { ROW_HOVER } from "@/lib/card-hover";
import { isGenerating, REPORT_POLL_MS, reportHref } from "@/lib/report";
import { cn } from "@/lib/utils";
import { HomeSection } from "@/components/app/home/HomeSection";
import { Button } from "@/components/ui/button";
import { REPORT_NOT_READY } from "./ReportCard";
import { ReportPrompt } from "./ReportPrompt";

const SUGGESTIONS = ["Summarize this record", "What went wrong?", "What went well?", "What is still open?"] as const;
const DATE = new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "short" });

/** The record's report list, shared by the Reports tab and its count (one query, polled while a report is written). */
export function useRecordReports(collectionId: number, recordId: number) {
  return useListRecordReports(collectionId, recordId, {
    query: {
      retry: false,
      refetchInterval: (query) => (query.state.data?.some((report) => isGenerating(report.state)) ? REPORT_POLL_MS : false),
    },
  });
}

interface RecordReportsProps {
  collectionId: number;
  recordId: number;
  /** The record's name for the question ("What do you want to know about …?"). */
  recordTitle?: string;
}

/**
 * The record's Reports tab like a chat start page: one question, one input and suggestions; sending creates the
 * report and opens its page, where the assistant writes it. The reports so far are listed below as plain rows.
 */
export function RecordReports({ collectionId, recordId, recordTitle }: RecordReportsProps) {
  const router = useRouter();
  const queryClient = useQueryClient();
  const { data: status } = useGetAiStatus({ query: { retry: false } });
  const { data: reports } = useRecordReports(collectionId, recordId);
  const create = useCreateReport({
    mutation: {
      onSuccess: (created) => {
        void queryClient.invalidateQueries({ queryKey: getListRecordReportsQueryKey(collectionId, recordId) });
        router.push(reportHref(collectionId, recordId, created.id));
      },
    },
  });
  const disabledReason = status !== undefined && !status.ready ? REPORT_NOT_READY : undefined;
  const ask = (prompt: string) => create.mutate({ collectionId, recordId, data: prompt ? { prompt } : {} });

  return (
    // Sits like the other sections of the page: left-aligned, the normal field look, the list under a section header.
    <section aria-label="Reports" className="grid max-w-3xl gap-8">
      <div className="grid gap-3">
        <h2 className="text-lg font-semibold tracking-tight">What do you want to know about {recordTitle ?? "this record"}?</h2>
        <ReportPrompt
          label="Ask about this record"
          placeholder="Ask about this record, or describe the report you want…"
          submitLabel="Create report"
          allowEmpty
          disabledReason={disabledReason}
          pending={create.isPending}
          onSubmit={ask}
        />
        {/* Ready-made questions, like the home page's quick actions; a click sends it. */}
        <ul aria-label="Suggestions" className="flex flex-wrap gap-2">
          {SUGGESTIONS.map((suggestion) => (
            <li key={suggestion}>
              <Button
                variant="outline"
                size="sm"
                className="font-normal shadow-none"
                disabled={disabledReason !== undefined || create.isPending}
                onClick={() => ask(suggestion)}
              >
                {suggestion}
              </Button>
            </li>
          ))}
        </ul>
      </div>

      {reports && reports.length > 0 ? (
        <HomeSection title="Reports">
          <ul aria-label="Reports on this record" className="-mx-2 grid">
            {reports.map((report) => (
              <li key={report.id}>
                <ReportRow report={report} href={reportHref(collectionId, recordId, report.id)} />
              </li>
            ))}
          </ul>
        </HomeSection>
      ) : null}
    </section>
  );
}

/** One report as a plain row: its title (or request), and on the right its date, or that it is still being written. */
function ReportRow({ report, href }: { report: ReportSummaryDto; href: string }) {
  return (
    <Link
      href={href}
      className={cn("flex items-center gap-3 rounded-md px-2 py-2 text-sm outline-none focus-visible:ring-2 focus-visible:ring-ring", ROW_HOVER)}
    >
      <span className="min-w-0 flex-1 truncate">{report.title ?? report.prompt ?? "Report about this record"}</span>
      {isGenerating(report.state) ? (
        <span className="flex shrink-0 items-center gap-1.5 text-xs text-muted-foreground">
          <LoaderCircle aria-hidden="true" className="size-3.5 animate-spin" />
          Writing…
        </span>
      ) : report.state === "FAILED" ? (
        <span className="shrink-0 text-xs text-destructive">Failed</span>
      ) : (
        <time dateTime={report.createdAt} className="shrink-0 text-xs text-muted-foreground tabular-nums">
          {DATE.format(new Date(report.createdAt))}
        </time>
      )}
    </Link>
  );
}
