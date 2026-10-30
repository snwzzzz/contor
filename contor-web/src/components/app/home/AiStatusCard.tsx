"use client";

import { usePathname } from "next/navigation";
import { useState, type ReactNode } from "react";
import { Progress } from "@/components/ui/progress";
import { Skeleton } from "@/components/ui/skeleton";
import { useGetAiStatus } from "@/lib/api/generated/endpoints/ai/ai";
import type { AiStatusDto } from "@/lib/api/generated/model/aiStatusDto";
import type { AnalysisRunDto } from "@/lib/api/generated/model/analysisRunDto";
import { isActiveRun, useLatestAnalysis } from "@/lib/hooks/useLatestAnalysis";
import { settingsHref } from "@/lib/navigation";
import { relativeTime } from "@/lib/relative-time";
import { cn } from "@/lib/utils";
import { HomeSection, SectionLink } from "./HomeSection";

function modelState(status: AiStatusDto): { label: string; dot: string } {
  if (!status.reachable) return { label: "Ollama not reachable", dot: "bg-destructive" };
  if (!status.ready) return { label: "Models missing", dot: "bg-warning" };
  return { label: "Ready", dot: "bg-success" };
}

/** The local model, its state, the analysis queue of a running run and when the last one finished. */
export function AiStatusCard() {
  const pathname = usePathname();
  const { data: status, isPending } = useGetAiStatus();
  const { run, isLoading } = useLatestAnalysis();
  // Fixed per mount: the run details are only rendered on the client, after they arrived.
  const [now] = useState(() => new Date());
  const model = status?.models.find((entry) => entry.roles.includes("EXTRACTION")) ?? status?.models[0];

  return (
    <HomeSection
      title="AI status"
      action={
        <SectionLink href={settingsHref(pathname, "ai")} scroll={false}>
          AI settings
        </SectionLink>
      }
    >
      {isPending || isLoading ? (
        <div aria-label="Loading AI status" className="grid gap-3">
          <Skeleton className="h-5 w-3/5" />
          <Skeleton className="h-5 w-4/5" />
        </div>
      ) : (
        <dl className="grid gap-2 py-1 text-sm">
          <Row label="Model">
            <span className="truncate">{model?.name ?? "None configured"}</span>
            {status ? (
              <span className="flex items-center gap-1.5 text-xs text-muted-foreground">
                <span aria-hidden="true" className={cn("size-1.5 rounded-full", modelState(status).dot)} />
                {modelState(status).label}
              </span>
            ) : null}
          </Row>
          {run && isActiveRun(run) ? <Queue run={run} /> : <LastRun run={run} now={now} />}
        </dl>
      )}
    </HomeSection>
  );
}

function Queue({ run }: { run: AnalysisRunDto }) {
  const percent = run.totalEmails > 0 ? Math.round((run.processedEmails / run.totalEmails) * 100) : 0;
  return (
    <Row label="Queue">
      <span className="text-xs text-muted-foreground tabular-nums">
        {run.processedEmails} of {run.totalEmails} emails
      </span>
      <Progress value={percent} aria-label="Analysis queue" />
    </Row>
  );
}

function LastRun({ run, now }: { run: AnalysisRunDto | undefined; now: Date }) {
  const finished = run?.finishedAt ?? run?.startedAt ?? run?.createdAt;
  return (
    <Row label="Last run">
      <span className="text-xs text-muted-foreground tabular-nums">
        {run && finished ? `${run.processedEmails} emails · ${relativeTime(finished, now)}` : "No analysis yet"}
      </span>
    </Row>
  );
}

function Row({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="grid grid-cols-[5rem_minmax(0,1fr)] items-start gap-3">
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className="grid min-w-0 gap-1">{children}</dd>
    </div>
  );
}
