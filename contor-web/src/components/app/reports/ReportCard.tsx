import { FileText, Link2, LoaderCircle, MessageSquare } from "lucide-react";
import Link from "next/link";
import type { ReportSummaryDto } from "@/lib/api/generated/model/reportSummaryDto";
import { isGenerating, reportStep, sourcesLabel } from "@/lib/report";

export const REPORT_NOT_READY = "The AI model is not ready: start Ollama and download the models in Settings → AI.";

const DATE_FORMAT = new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" });

/**
 * One report in a list of reports: title, request, its progress while it is generated, else its first sentence, how
 * many sentences have a source and its follow-ups.
 */
export function ReportCard({ href, report, collection }: { href: string; report: ReportSummaryDto; collection: boolean }) {
  const generating = isGenerating(report.state);
  return (
    <Link
      href={href}
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
          {reportStep(report.state, collection)}
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
