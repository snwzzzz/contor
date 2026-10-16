"use client";

import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import type { ReportSourceDto } from "@/lib/api/generated/model/reportSourceDto";
import { formatValue } from "@/lib/proposal-format";
import { cn } from "@/lib/utils";

interface CitationMarkerProps {
  marker: number;
  source: ReportSourceDto | undefined;
  active: boolean;
  onSelect: (marker: number) => void;
}

/** A source marker [n] after a sentence: hovering shows what it says, a click opens it in the sources panel. */
export function CitationMarker({ marker, source, active, onSelect }: CitationMarkerProps) {
  const preview = source === undefined ? undefined
    : source.kind === "FIELD" ? `${source.recordTitle ? `${source.recordTitle} · ` : ""}${source.fieldName ?? source.label}: ${formatValue(source.value)}`
    : source.kind === "EMAIL_PARAGRAPH" ? source.quote
    : source.recordTitle ?? source.label;
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <button
          type="button"
          aria-label={`Source ${marker}: ${source?.label ?? "unknown"}`}
          aria-pressed={active}
          onClick={() => onSelect(marker)}
          className={cn(
            "mx-0.5 inline-flex h-4 min-w-4 items-center justify-center rounded px-1 align-super text-[0.65rem] font-medium tabular-nums transition-colors",
            active ? "bg-primary text-primary-foreground" : "bg-muted text-muted-foreground hover:bg-primary/15 hover:text-primary",
            source?.sourceState === "CHANGED" || source?.sourceState === "DELETED" ? "ring-1 ring-warning" : undefined,
          )}
        >
          {marker}
        </button>
      </TooltipTrigger>
      {preview ? <TooltipContent className="max-w-xs">{preview}</TooltipContent> : null}
    </Tooltip>
  );
}
