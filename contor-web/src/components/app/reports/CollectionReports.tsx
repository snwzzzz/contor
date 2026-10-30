"use client";

import { useQueryClient } from "@tanstack/react-query";
import { FileText } from "lucide-react";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { AppButton } from "@/components/app/AppButton";
import { DetailSheet } from "@/components/app/DetailSheet";
import { useGetAiStatus } from "@/lib/api/generated/endpoints/ai/ai";
import { getListCollectionReportsQueryKey, useCreateCollectionReport, useListCollectionReports } from "@/lib/api/generated/endpoints/reports/reports";
import type { NewCollectionReportCommand } from "@/lib/api/generated/model/newCollectionReportCommand";
import { isGenerating, REPORT_POLL_MS, reportHref } from "@/lib/report";
import { ReportPrompt } from "./ReportPrompt";
import { REPORT_NOT_READY, ReportRow } from "./ReportRow";

const SUGGESTIONS = ["Give an overview", "Top 5 by count", "What stands out?", "Pros and cons"] as const;

interface CollectionReportsProps {
  collectionId: number;
  collectionName: string;
  /** The table's current filters (`field:op:value`) and search: a report covers the records the table shows. */
  filters: readonly string[];
  search: string;
  /** How many records the table shows, for the hint when filters or a search narrow the report. */
  matching?: number;
}

/**
 * Reports over the whole collection, out of the page's way: a toolbar button (with the number of reports) opens a side
 * sheet with the question input (the table's filters and search are the scope) and the reports so far as plain rows.
 * Sending opens the new report's page, where the assistant writes it.
 */
export function CollectionReports({ collectionId, collectionName, filters, search, matching }: CollectionReportsProps) {
  const queryClient = useQueryClient();
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const { data: status } = useGetAiStatus({ query: { retry: false } });
  const { data: reports } = useListCollectionReports(collectionId, {
    query: {
      retry: false,
      refetchInterval: (query) => (query.state.data?.some((report) => isGenerating(report.state)) ? REPORT_POLL_MS : false),
    },
  });
  const create = useCreateCollectionReport({
    mutation: {
      onSuccess: (created) => {
        void queryClient.invalidateQueries({ queryKey: getListCollectionReportsQueryKey(collectionId) });
        router.push(reportHref(collectionId, undefined, created.id));
      },
    },
  });
  const disabledReason = status !== undefined && !status.ready ? REPORT_NOT_READY : undefined;
  const narrowed = filters.length > 0 || search !== "";
  const writing = reports?.some((report) => isGenerating(report.state)) ?? false;

  function ask(prompt: string) {
    const data: NewCollectionReportCommand = {};
    if (prompt) data.prompt = prompt;
    if (filters.length > 0) data.filters = [...filters];
    if (search) data.search = search;
    create.mutate({ collectionId, data });
  }

  return (
    <>
      <AppButton variant="ghost" size="sm" className="h-9" onClick={() => setOpen(true)}>
        <FileText aria-hidden="true" data-icon="inline-start" />
        Reports
        {/* Always the same slot, so the toolbar does not move when the count arrives. */}
        <span className="min-w-4 text-xs text-muted-foreground tabular-nums">{reports && reports.length > 0 ? reports.length : null}</span>
        {writing ? <span className="sr-only">(one is being written)</span> : null}
      </AppButton>

      <DetailSheet open={open} onOpenChange={setOpen} title={`Reports on ${collectionName}`}>
        <div className="grid gap-5">
          <div className="grid gap-2">
            <ReportPrompt
              label={`Ask about ${collectionName}`}
              placeholder={`Ask about all ${collectionName.toLowerCase()}, e.g. “top 5 by revenue”…`}
              submitLabel="Create report"
              suggestions={SUGGESTIONS}
              allowEmpty
              disabledReason={disabledReason}
              pending={create.isPending}
              onSubmit={ask}
            />
            {narrowed ? (
              <p className="pl-1 text-xs text-muted-foreground">
                {matching === undefined
                  ? "Covers the records the table shows (filters and search)."
                  : `Covers the ${matching} ${matching === 1 ? "record" : "records"} the table shows (filters and search).`}
              </p>
            ) : null}
          </div>

          <div className="grid gap-1">
            <h3 className="text-sm font-medium">Reports</h3>
            {reports && reports.length === 0 ? (
              <p className="py-2 text-xs text-muted-foreground">No reports yet</p>
            ) : (
              <ul className="-mx-2 grid">
                {(reports ?? []).map((report) => (
                  <li key={report.id}>
                    <ReportRow report={report} fallback={`Report on ${collectionName}`} href={reportHref(collectionId, undefined, report.id)} />
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>
      </DetailSheet>
    </>
  );
}
