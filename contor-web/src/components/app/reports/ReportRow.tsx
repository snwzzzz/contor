import { LoaderCircle } from "lucide-react";
import Link from "next/link";
import type { ReportSummaryDto } from "@/lib/api/generated/model/reportSummaryDto";
import { ROW_HOVER } from "@/lib/card-hover";
import { isGenerating } from "@/lib/report";
import { cn } from "@/lib/utils";

export const REPORT_NOT_READY = "The AI model is not ready: start Ollama and download the models in Settings → AI.";

const DATE = new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "short" });

const ROW = "flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-left text-sm outline-none focus-visible:ring-2 focus-visible:ring-ring";

interface ReportRowProps {
  report: ReportSummaryDto;
  /** Shown when the report has neither a title nor a request yet. */
  fallback: string;
  /** A link to the report's page, or `onSelect` to show it in place. */
  href?: string;
  onSelect?: () => void;
  current?: boolean;
}

/** One report as a plain row: its title (or request), and on the right its date, or that it is still being written. */
export function ReportRow({ report, fallback, href, onSelect, current = false }: ReportRowProps) {
  const className = cn(ROW, ROW_HOVER, current && "bg-accent font-medium hover:bg-accent");
  const content = (
    <>
      <span className="min-w-0 flex-1 truncate">{report.title ?? report.prompt ?? fallback}</span>
      {isGenerating(report.state) ? (
        <span className="flex shrink-0 items-center gap-1 text-xs font-normal text-muted-foreground">
          <LoaderCircle aria-hidden="true" className="size-3 animate-spin" />
          Writing…
        </span>
      ) : report.state === "FAILED" ? (
        <span className="shrink-0 text-xs font-normal text-destructive">Failed</span>
      ) : (
        <time dateTime={report.createdAt} className="shrink-0 text-xs font-normal text-muted-foreground tabular-nums">
          {DATE.format(new Date(report.createdAt))}
        </time>
      )}
    </>
  );
  return href ? (
    <Link href={href} className={className}>
      {content}
    </Link>
  ) : (
    <button type="button" aria-current={current ? "true" : undefined} onClick={onSelect} className={className}>
      {content}
    </button>
  );
}
