"use client";

import { useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { AppButton } from "@/components/app/AppButton";
import { useGetAiStatus } from "@/lib/api/generated/endpoints/ai/ai";
import {
  getListCollectionReportsQueryKey,
  useCreateCollectionReport,
  useListCollectionReports,
} from "@/lib/api/generated/endpoints/reports/reports";
import type { NewCollectionReportCommand } from "@/lib/api/generated/model/newCollectionReportCommand";
import { isGenerating, REPORT_POLL_MS, reportHref } from "@/lib/report";
import { REPORT_NOT_READY, ReportCard } from "./ReportCard";
import { ReportPrompt } from "./ReportPrompt";

const SUGGESTIONS = ["Give an overview", "Top 5 by count", "What stands out?", "Pros and cons"] as const;
/** Reports shown before "Show all". */
const NEWEST = 3;

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
 * Reports over the whole collection on its page: a prompt for questions about many records ("top 5 customers by
 * sales") with the table's filters and search as the scope, and the reports so far, newest first.
 */
export function CollectionReports({ collectionId, collectionName, filters, search, matching }: CollectionReportsProps) {
  const queryClient = useQueryClient();
  const [showAll, setShowAll] = useState(false);
  const { data: status } = useGetAiStatus({ query: { retry: false } });
  const { data: reports } = useListCollectionReports(collectionId, {
    query: {
      retry: false,
      refetchInterval: (query) => (query.state.data?.some((report) => isGenerating(report.state)) ? REPORT_POLL_MS : false),
    },
  });
  const create = useCreateCollectionReport({
    mutation: { onSuccess: () => queryClient.invalidateQueries({ queryKey: getListCollectionReportsQueryKey(collectionId) }) },
  });
  const disabledReason = status !== undefined && !status.ready ? REPORT_NOT_READY : undefined;
  const narrowed = filters.length > 0 || search !== "";
  const shown = showAll ? reports ?? [] : (reports ?? []).slice(0, NEWEST);

  function ask(prompt: string) {
    const data: NewCollectionReportCommand = {};
    if (prompt) data.prompt = prompt;
    if (filters.length > 0) data.filters = [...filters];
    if (search) data.search = search;
    create.mutate({ collectionId, data });
  }

  return (
    <section aria-label={`Reports on ${collectionName}`} className="grid gap-3">
      <ReportPrompt
        label={`Ask about ${collectionName}`}
        placeholder={`Ask about all ${collectionName.toLowerCase()}, e.g. “top 5 by revenue” or “which have the most complaints?”…`}
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
      {shown.length > 0 ? (
        <ul aria-label={`Reports on ${collectionName}`} className="grid gap-2 md:grid-cols-2 xl:grid-cols-3">
          {shown.map((report) => (
            <li key={report.id}>
              <ReportCard href={reportHref(collectionId, undefined, report.id)} report={report} collection />
            </li>
          ))}
        </ul>
      ) : null}
      {!showAll && reports !== undefined && reports.length > NEWEST ? (
        <AppButton variant="ghost" size="sm" className="w-fit" onClick={() => setShowAll(true)}>
          Show all {reports.length} reports
        </AppButton>
      ) : null}
    </section>
  );
}
