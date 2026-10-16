"use client";

import { useQueryClient } from "@tanstack/react-query";
import { useGetAiStatus } from "@/lib/api/generated/endpoints/ai/ai";
import { getListRecordReportsQueryKey, useCreateReport, useListRecordReports } from "@/lib/api/generated/endpoints/reports/reports";
import { isGenerating, REPORT_POLL_MS, reportHref } from "@/lib/report";
import { REPORT_NOT_READY, ReportCard } from "./ReportCard";
import { ReportPrompt } from "./ReportPrompt";

const SUGGESTIONS = ["Summarize this record", "What went wrong?", "What went well?", "What is still open?"] as const;

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
  const disabledReason = status !== undefined && !status.ready ? REPORT_NOT_READY : undefined;

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
              <ReportCard href={reportHref(collectionId, recordId, report.id)} report={report} collection={false} />
            </li>
          ))}
        </ul>
      ) : null}
    </section>
  );
}
