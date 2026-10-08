"use client";

import { useQueryClient } from "@tanstack/react-query";
import { CalendarDays, ListTodo, Sparkles } from "lucide-react";
import Link from "next/link";
import { AppButton } from "@/components/app/AppButton";
import { ErrorMessage } from "@/components/app/ErrorMessage";
import { useCancelAnalysis, useContinueAnalysis } from "@/lib/api/generated/endpoints/ai/ai";
import type { AnalysisRunDto } from "@/lib/api/generated/model/analysisRunDto";
import type { AnalysisState } from "@/lib/api/generated/model/analysisState";
import { isActiveRun, useLatestAnalysis } from "@/lib/hooks/useLatestAnalysis";
import { CategoryReview } from "./CategoryReview";

const STATE_TEXT: Record<AnalysisState, string> = {
  QUEUED: "Waiting to start…",
  DISCOVERING: "Looking for categories in your inbox…",
  AWAITING_REVIEW: "The assistant found these categories. Pick the ones to keep.",
  EXTRACTING: "Analyzing emails…",
  COMPLETED: "Analysis finished.",
  FAILED: "The analysis failed.",
  CANCELLED: "The analysis was stopped.",
};

/** The latest inbox analysis: progress, counters, the category review, and what to look at afterwards. */
export function AnalysisProgress() {
  const queryClient = useQueryClient();
  const { run, isLoading, queryKey } = useLatestAnalysis();
  const onRun = { mutation: { onSuccess: (data: AnalysisRunDto) => queryClient.setQueryData(queryKey, data) } };
  const cancel = useCancelAnalysis(onRun);
  const resume = useContinueAnalysis(onRun);

  if (isLoading) {
    return <p className="text-sm text-muted-foreground">Loading…</p>;
  }
  if (!run) {
    return <p className="text-sm text-muted-foreground">No analysis has run yet.</p>;
  }

  const showProgress = run.status === "EXTRACTING" || run.status === "COMPLETED" || run.status === "CANCELLED";
  return (
    <div className="grid gap-5">
      <p className="text-sm font-medium" aria-live="polite">
        {STATE_TEXT[run.status]}
      </p>
      {run.status === "FAILED" ? <ErrorMessage message={run.error ?? "Unknown error"} /> : null}
      {showProgress ? <ProgressBar run={run} /> : null}

      <dl className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        <Counter label="Waiting for review" value={run.proposalsCreated} />
        <Counter label="Applied automatically" value={run.autoApplied} />
        <Counter label="Categories found" value={run.categoriesDiscovered} />
        <Counter label="Failed emails" value={run.failedEmails} />
      </dl>

      {run.status === "AWAITING_REVIEW" ? (
        <div className="grid gap-4">
          <CategoryReview />
          <AppButton className="w-fit" loading={resume.isPending} onClick={() => resume.mutate({ runId: run.id })}>
            Continue analysis
          </AppButton>
        </div>
      ) : null}

      {isActiveRun(run) ? (
        <AppButton variant="outline" className="w-fit" loading={cancel.isPending} onClick={() => cancel.mutate({ runId: run.id })}>
          Stop analysis
        </AppButton>
      ) : null}

      {run.status === "COMPLETED" ? (
        <nav aria-label="Results" className="flex flex-wrap gap-2">
          <ResultLink href="/proposals" icon={Sparkles} label="Review proposals" />
          <ResultLink href="/calendar" icon={CalendarDays} label="Open calendar" />
          <ResultLink href="/tasks" icon={ListTodo} label="Open to-dos" />
        </nav>
      ) : null}
    </div>
  );
}

function ProgressBar({ run }: { run: AnalysisRunDto }) {
  const percent = run.totalEmails > 0 ? Math.round((run.processedEmails / run.totalEmails) * 100) : 100;
  return (
    <div className="grid gap-1.5">
      <div
        role="progressbar"
        aria-label="Emails analyzed"
        aria-valuemin={0}
        aria-valuemax={run.totalEmails}
        aria-valuenow={run.processedEmails}
        className="h-2 overflow-hidden rounded-full bg-muted"
      >
        <div className="h-full bg-primary transition-[width]" style={{ width: `${percent}%` }} />
      </div>
      <span className="text-xs text-muted-foreground">
        {run.processedEmails} of {run.totalEmails} emails
      </span>
    </div>
  );
}

function Counter({ label, value }: { label: string; value: number }) {
  return (
    <div className="grid gap-0.5 rounded-lg border px-3 py-2">
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className="text-lg font-semibold tabular-nums">{value}</dd>
    </div>
  );
}

function ResultLink({ href, icon: Icon, label }: { href: string; icon: typeof Sparkles; label: string }) {
  return (
    <Link href={href} className="inline-flex items-center gap-2 rounded-md border px-3 py-1.5 text-sm hover:bg-accent">
      <Icon className="size-4" aria-hidden="true" />
      {label}
    </Link>
  );
}
