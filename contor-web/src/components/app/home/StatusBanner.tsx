"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import type { ReactNode } from "react";
import { Button } from "@/components/ui/button";
import { Progress } from "@/components/ui/progress";
import { Skeleton } from "@/components/ui/skeleton";
import { useCountProposals } from "@/lib/api/generated/endpoints/proposals/proposals";
import type { AnalysisRunDto } from "@/lib/api/generated/model/analysisRunDto";
import { isActiveRun, useLatestAnalysis } from "@/lib/hooks/useLatestAnalysis";
import { settingsHref } from "@/lib/navigation";
import { cn } from "@/lib/utils";

const PENDING_ACTIONS = { status: "PENDING", group: "ACTIONS" } as const;

function percent(run: AnalysisRunDto): number {
  return run.totalEmails > 0 ? Math.round((run.processedEmails / run.totalEmails) * 100) : 0;
}

function runTitle(run: AnalysisRunDto): string {
  switch (run.status) {
    case "EXTRACTING":
      return `AI is analyzing ${run.processedEmails} of ${run.totalEmails} emails`;
    case "AWAITING_REVIEW":
      return "AI found categories for you to review";
    default:
      return "AI is looking for categories in your mail";
  }
}

/**
 * The most relevant state as one quiet line under the greeting: a running analysis (with progress), proposals waiting
 * for review, or "All caught up".
 */
export function StatusBanner() {
  const pathname = usePathname();
  const { run, isLoading } = useLatestAnalysis();
  const { data: pending, isPending } = useCountProposals(PENDING_ACTIONS);

  if (isLoading || isPending) {
    return <Skeleton className="h-5 w-72" aria-label="Loading status" />;
  }
  if (run && isActiveRun(run)) {
    return (
      <StatusLine
        active
        title={runTitle(run)}
        action={
          <StatusLink href={settingsHref(pathname, "autonomy")} overlay>
            View progress
          </StatusLink>
        }
      >
        {run.status === "EXTRACTING" ? <Progress value={percent(run)} aria-label="Analysis progress" className="w-32" /> : null}
      </StatusLine>
    );
  }
  const count = pending?.count ?? 0;
  if (count > 0) {
    return (
      <StatusLine
        active
        title={`${count} ${count === 1 ? "proposal" : "proposals"} waiting for review`}
        action={<StatusLink href="/proposals">Review now</StatusLink>}
      />
    );
  }
  return <StatusLine title="All caught up" detail="Nothing waits for a decision." />;
}

interface StatusLineProps {
  /** Something is going on: the dot takes the info color (the only color in the hero). */
  active?: boolean;
  title: string;
  detail?: string;
  action?: ReactNode;
  children?: ReactNode;
}

function StatusLine({ active, title, detail, action, children }: StatusLineProps) {
  return (
    <div role="status" className="flex flex-wrap items-center gap-x-3 gap-y-2 text-sm text-muted-foreground">
      <span aria-hidden="true" className={cn("size-1.5 shrink-0 rounded-full", active ? "bg-info" : "bg-success")} />
      <span className="text-foreground">{title}</span>
      {detail ? <span>{detail}</span> : null}
      {children}
      {action}
    </div>
  );
}

/** `overlay`: the link opens a modal over this page (keep the scroll position). */
function StatusLink({ href, overlay, children }: { href: string; overlay?: boolean; children: ReactNode }) {
  return (
    <Button asChild variant="outline" size="xs">
      <Link href={href} scroll={overlay ? false : undefined}>
        {children}
      </Link>
    </Button>
  );
}
