"use client";

import { useQueryClient } from "@tanstack/react-query";
import { PanelLeftClose, PanelLeftOpen, SquarePen } from "lucide-react";
import { useDeferredValue, useState } from "react";
import { SearchField } from "@/components/app/SearchField";
import { Button } from "@/components/ui/button";
import { useGetAiStatus } from "@/lib/api/generated/endpoints/ai/ai";
import { getListRecordReportsQueryKey, useCreateReport, useListRecordReports } from "@/lib/api/generated/endpoints/reports/reports";
import type { ReportSummaryDto } from "@/lib/api/generated/model/reportSummaryDto";
import { isGenerating, REPORT_POLL_MS } from "@/lib/report";
import { useStoredValue, writeStoredValue } from "@/lib/stored-value";
import { cn } from "@/lib/utils";
import { ReportPrompt } from "./ReportPrompt";
import { REPORT_NOT_READY, ReportRow } from "./ReportRow";
import { ReportView } from "./ReportView";

const SUGGESTIONS = ["Summarize this record", "What went wrong?", "What went well?", "What is still open?"] as const;

/** Whether the report list is open: a per-browser preference. */
const LIST_OPEN_KEY = "contor.reports.listOpen";
const parseOpen = (raw: unknown) => (typeof raw === "boolean" ? raw : undefined);

/** The record's report list, shared by the Reports tab and its count (one query, polled while a report is written). */
export function useRecordReports(collectionId: number, recordId: number) {
  return useListRecordReports(collectionId, recordId, {
    query: {
      retry: false,
      refetchInterval: (query) => (query.state.data?.some((report) => isGenerating(report.state)) ? REPORT_POLL_MS : false),
    },
  });
}

function matches(report: ReportSummaryDto, text: string): boolean {
  const needle = text.trim().toLowerCase();
  return !needle || [report.title, report.prompt, report.summary].some((part) => part?.toLowerCase().includes(needle));
}

interface RecordReportsProps {
  collectionId: number;
  recordId: number;
  /** The record's name for the question ("What do you want to know about …?"). */
  recordTitle?: string;
}

/**
 * The record's Reports tab as a workspace: a closable side list of its reports (search, New report) and, next to it,
 * either the question with the input (sending creates the report and shows it here while the assistant writes it) or
 * the selected report itself with its follow-up chat; "Open full screen" opens the report's own page.
 */
export function RecordReports({ collectionId, recordId, recordTitle }: RecordReportsProps) {
  const queryClient = useQueryClient();
  const { data: status } = useGetAiStatus({ query: { retry: false } });
  const { data: reports } = useRecordReports(collectionId, recordId);
  const [selected, setSelected] = useState<number | null>(null);
  const listOpen = useStoredValue(LIST_OPEN_KEY, true, parseOpen);
  const create = useCreateReport({
    mutation: {
      onSuccess: (created) => {
        void queryClient.invalidateQueries({ queryKey: getListRecordReportsQueryKey(collectionId, recordId) });
        setSelected(created.id);
      },
    },
  });
  const disabledReason = status !== undefined && !status.ready ? REPORT_NOT_READY : undefined;
  const ask = (prompt: string) => create.mutate({ collectionId, recordId, data: prompt ? { prompt } : {} });

  return (
    // The whole page: full width and, on large screens, the rest of the screen below the tab bar (the record page gives
    // its tab panel that height). The list and the detail scroll on their own; the divider runs the full height.
    <section
      aria-label="Reports"
      className={cn(
        "grid gap-6 lg:h-full lg:min-h-0 lg:flex-1 lg:grid-rows-[minmax(0,1fr)] lg:gap-0",
        // The list slides in and out: its column animates between 16rem and 0 (it stays mounted while it moves).
        "lg:transition-[grid-template-columns] lg:duration-200 lg:ease-out motion-reduce:transition-none",
        listOpen ? "lg:grid-cols-[16rem_minmax(0,1fr)]" : "lg:grid-cols-[0rem_minmax(0,1fr)]",
      )}
    >
      <ReportList
        open={listOpen}
        reports={reports}
        selected={selected}
        onSelect={setSelected}
        onNew={() => setSelected(null)}
        onHide={() => writeStoredValue(LIST_OPEN_KEY, false)}
      />

      {/* Scrolls on its own, never sideways; pb-6 keeps room for the report's sticky follow-up input at the bottom. */}
      <div
        data-testid="report-detail"
        className={cn(
          "flex min-h-0 min-w-0 flex-col gap-4 lg:overflow-x-hidden lg:overflow-y-auto lg:pb-6 lg:transition-[padding] lg:duration-200",
          listOpen && "lg:pl-6",
        )}
      >
        {/* The open button appears here while the list is closed; the row keeps its height in both states, so opening or
            closing the list never moves the content up or down. */}
        <div className="flex h-8 shrink-0 items-center">
          {listOpen ? null : (
            <Button
              variant="ghost"
              size="icon-sm"
              aria-label="Show report list"
              title="Show report list"
              className="text-muted-foreground animate-in fade-in duration-200 motion-reduce:animate-none"
              onClick={() => writeStoredValue(LIST_OPEN_KEY, true)}
            >
              <PanelLeftOpen aria-hidden="true" />
            </Button>
          )}
        </div>
        {selected === null ? (
          <AskStart recordTitle={recordTitle} disabledReason={disabledReason} pending={create.isPending} onAsk={ask} />
        ) : (
          <ReportView key={selected} collectionId={collectionId} recordId={recordId} reportId={selected} embedded onNavigate={setSelected} />
        )}
      </div>
    </section>
  );
}

interface AskStartProps {
  recordTitle: string | undefined;
  disabledReason: string | undefined;
  pending: boolean;
  onAsk: (prompt: string) => void;
}

/** A new question: the question line, the input and ready-made questions. */
function AskStart({ recordTitle, disabledReason, pending, onAsk }: AskStartProps) {
  return (
    // Centered in its area, like a chat start page.
    <div className="mx-auto flex min-h-80 w-full max-w-3xl flex-1 flex-col justify-center gap-3 pb-[10vh]">
      <h2 className="text-center text-xl font-semibold tracking-tight">What do you want to know about {recordTitle ?? "this record"}?</h2>
      <ReportPrompt
        label="Ask about this record"
        placeholder="Ask about this record, or describe the report you want…"
        submitLabel="Create report"
        allowEmpty
        disabledReason={disabledReason}
        pending={pending}
        onSubmit={onAsk}
      />
      <ul aria-label="Suggestions" className="flex flex-wrap justify-center gap-2">
        {SUGGESTIONS.map((suggestion) => (
          <li key={suggestion}>
            <Button
              variant="outline"
              size="sm"
              className="font-normal shadow-none"
              disabled={disabledReason !== undefined || pending}
              onClick={() => onAsk(suggestion)}
            >
              {suggestion}
            </Button>
          </li>
        ))}
      </ul>
    </div>
  );
}

interface ReportListProps {
  /** Closed: slid away (zero width), hidden from everyone and from the keyboard (inert). */
  open: boolean;
  reports: ReportSummaryDto[] | undefined;
  selected: number | null;
  onSelect: (reportId: number) => void;
  onNew: () => void;
  onHide: () => void;
}

/** The side list: New report, search and one plain row per report (no boxes; a hairline sets it apart). */
function ReportList({ open, reports, selected, onSelect, onNew, onHide }: ReportListProps) {
  const [query, setQuery] = useState("");
  const text = useDeferredValue(query);
  const shown = (reports ?? []).filter((report) => matches(report, text));

  return (
    <aside
      aria-label="Report list"
      aria-hidden={open ? undefined : true}
      inert={!open}
      className={cn(
        "min-h-0 min-w-0 overflow-hidden lg:h-full lg:transition-opacity lg:duration-200 motion-reduce:transition-none",
        open ? "lg:border-r lg:opacity-100" : "max-lg:hidden lg:opacity-0",
      )}
    >
      {/* Fixed width, so the content does not reflow while the column slides. */}
      <div className="flex h-full min-h-0 flex-col gap-2 lg:w-60">
        <div className="flex h-8 items-center gap-1">
          <h3 className="mr-auto text-sm font-medium">Reports</h3>
          <Button variant="ghost" size="icon-sm" aria-label="New report" title="New report" className="text-muted-foreground" onClick={onNew}>
            <SquarePen aria-hidden="true" />
          </Button>
          <Button variant="ghost" size="icon-sm" aria-label="Hide report list" title="Hide report list" className="text-muted-foreground" onClick={onHide}>
            <PanelLeftClose aria-hidden="true" />
          </Button>
        </div>
        <SearchField
          aria-label="Search reports"
          placeholder="Search reports…"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          onClear={() => setQuery("")}
        />
        {reports && reports.length === 0 ? (
          <p className="px-2 py-4 text-xs text-muted-foreground">No reports yet</p>
        ) : reports && shown.length === 0 ? (
          <p className="px-2 py-4 text-xs text-muted-foreground">No report matches</p>
        ) : (
          <div data-slot="report-list-scroll" className="-mx-2 min-h-0 flex-1 overflow-y-auto px-2">
            <ul className="-mx-2 grid content-start">
              {shown.map((report) => (
                <li key={report.id}>
                  <ReportRow report={report} fallback="Report about this record" current={report.id === selected} onSelect={() => onSelect(report.id)} />
                </li>
              ))}
            </ul>
          </div>
        )}
      </div>
    </aside>
  );
}
