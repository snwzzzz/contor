"use client";

import { PenLine } from "lucide-react";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import type { FindingLevel } from "@/lib/api/generated/model/findingLevel";
import type { ReportClaimDto } from "@/lib/api/generated/model/reportClaimDto";
import type { ReportDto } from "@/lib/api/generated/model/reportDto";
import type { ReportFindingDto } from "@/lib/api/generated/model/reportFindingDto";
import type { ReportSectionDto } from "@/lib/api/generated/model/reportSectionDto";
import type { ReportSourceDto } from "@/lib/api/generated/model/reportSourceDto";
import { cn } from "@/lib/utils";
import { CitationMarker } from "./CitationMarker";

const LEVEL: Record<FindingLevel, { label: string; className: string }> = {
  POSITIVE: { label: "Went well", className: "bg-success/15 text-success" },
  NEUTRAL: { label: "Note", className: "bg-muted text-muted-foreground" },
  PROBLEM: { label: "Problem", className: "bg-destructive/10 text-destructive" },
  RISK: { label: "Risk", className: "bg-warning/15 text-warning" },
  CONFLICT: { label: "Sources disagree", className: "bg-warning/20 text-warning" },
};

const DAY_FORMAT = new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "short", year: "numeric", timeZone: "UTC" });

interface Citations {
  sources: ReadonlyMap<number, ReportSourceDto>;
  selected: number | undefined;
  onSelect: (marker: number) => void;
}

/** The sections of one report in reading order; every sentence carries its source markers. */
export function ReportBody({ report, selected, onSelect }: { report: ReportDto; selected: number | undefined; onSelect: (marker: number) => void }) {
  const citations: Citations = { sources: new Map(report.sources.map((source) => [source.marker, source])), selected, onSelect };
  return (
    <div className="grid gap-8">
      {report.sections.map((section) => (
        <Section key={section.id} section={section} citations={citations} />
      ))}
    </div>
  );
}

function Section({ section, citations }: { section: ReportSectionDto; citations: Citations }) {
  return (
    <section aria-labelledby={`report-section-${section.id}`} className="grid gap-3">
      <h2 id={`report-section-${section.id}`} className="text-lg font-semibold tracking-tight">{section.title}</h2>
      {section.type === "SUMMARY" ? (
        <p className="text-sm leading-7">
          {section.claims.map((claim) => (
            <Claim key={claim.id} claim={claim} citations={citations} trailingSpace />
          ))}
        </p>
      ) : section.type === "TIMELINE" ? (
        <ol className="grid gap-2 border-l pl-4">
          {section.findings.map((finding) => (
            <li key={finding.id} className="relative text-sm">
              <span aria-hidden="true" className="absolute top-1.5 -left-[1.3rem] size-2 rounded-full bg-primary" />
              <span className="mr-2 text-xs font-medium text-muted-foreground tabular-nums">
                {finding.eventDate ? DAY_FORMAT.format(new Date(finding.eventDate)) : finding.title}
              </span>
              {finding.claims.map((claim) => (
                <Claim key={claim.id} claim={claim} citations={citations} />
              ))}
            </li>
          ))}
        </ol>
      ) : (
        <>
          {section.findings.map((finding) => (
            <Finding key={finding.id} finding={finding} citations={citations} />
          ))}
          {section.claims.length > 0 ? (
            <ul className="grid list-disc gap-1.5 pl-5 text-sm leading-6">
              {section.claims.map((claim) => (
                <li key={claim.id}>
                  <Claim claim={claim} citations={citations} />
                </li>
              ))}
            </ul>
          ) : null}
        </>
      )}
    </section>
  );
}

function Finding({ finding, citations }: { finding: ReportFindingDto; citations: Citations }) {
  const level = LEVEL[finding.level];
  return (
    <article aria-label={finding.title} className="grid gap-2 rounded-xl border p-4">
      <header className="flex flex-wrap items-center gap-2">
        <span className={cn("rounded-full px-2 py-0.5 text-xs font-medium", level.className)}>{level.label}</span>
        <h3 className="text-sm font-semibold">{finding.title}</h3>
      </header>
      <p className="text-sm leading-6">
        {finding.claims.map((claim) => (
          <Claim key={claim.id} claim={claim} citations={citations} trailingSpace />
        ))}
      </p>
      {finding.quotes.map((quote) => (
        <blockquote key={`${quote.marker}-${quote.text}`} className="border-l-2 border-primary/40 pl-3 text-sm text-muted-foreground italic">
          <p>“{quote.text}”</p>
          <footer className="mt-0.5 text-xs not-italic">
            {citations.sources.get(quote.marker)?.emailFrom ?? citations.sources.get(quote.marker)?.label} · source {quote.marker}
          </footer>
        </blockquote>
      ))}
    </article>
  );
}

function Claim({ claim, citations, trailingSpace = false }: { claim: ReportClaimDto; citations: Citations; trailingSpace?: boolean }) {
  return (
    <>
      <span>{claim.text}</span>
      {claim.check === "REPAIRED" ? (
        <Tooltip>
          <TooltipTrigger asChild>
            <span role="img" aria-label="Rewritten by the fact check" className="ml-0.5 inline-flex align-super text-muted-foreground">
              <PenLine className="size-3" aria-hidden="true" />
            </span>
          </TooltipTrigger>
          <TooltipContent className="max-w-xs">{claim.note ?? "Rewritten by the fact check"}</TooltipContent>
        </Tooltip>
      ) : null}
      {claim.markers.map((marker) => (
        <CitationMarker key={marker} marker={marker} source={citations.sources.get(marker)} active={citations.selected === marker}
          onSelect={citations.onSelect} />
      ))}
      {trailingSpace ? " " : null}
    </>
  );
}
