"use client";

import { useQueryClient } from "@tanstack/react-query";
import { CalendarDays, ListTodo, ScanSearch, Sparkles } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { AnimatedDots } from "@/components/app/AnimatedDots";
import { AppButton } from "@/components/app/AppButton";
import { ErrorMessage } from "@/components/app/ErrorMessage";
import { useCancelAnalysis, useContinueAnalysis } from "@/lib/api/generated/endpoints/ai/ai";
import type { AnalysisRunDto } from "@/lib/api/generated/model/analysisRunDto";
import type { AnalysisState } from "@/lib/api/generated/model/analysisState";
import { formatDuration, secondsPerEmail, type ProgressSample } from "@/lib/analysis-eta";
import { isActiveRun, useLatestAnalysis } from "@/lib/hooks/useLatestAnalysis";
import { CategoryReview } from "./CategoryReview";

const STATE_TEXT: Record<AnalysisState, string> = {
  QUEUED: "Waiting to start",
  DISCOVERING: "Looking for categories in your inbox",
  AWAITING_REVIEW: "The assistant found these categories. Pick the ones to keep.",
  EXTRACTING: "Analyzing emails",
  COMPLETED: "Analysis finished.",
  FAILED: "The analysis failed.",
  CANCELLED: "The analysis was stopped.",
};

/** The latest inbox analysis: progress, counters, the category review, and what to look at afterwards. */
export function AnalysisProgress() {
  const queryClient = useQueryClient();
  const { run, isLoading, queryKey, updatedAt } = useLatestAnalysis();
  const onRun = { mutation: { onSuccess: (data: AnalysisRunDto) => queryClient.setQueryData(queryKey, data) } };
  const cancel = useCancelAnalysis(onRun);
  const resume = useContinueAnalysis(onRun);

  if (isLoading) {
    return <p className="text-sm text-muted-foreground">Loading…</p>;
  }
  if (!run) {
    return <p className="text-sm text-muted-foreground">No analysis has run yet.</p>;
  }

  const showEmails = run.status === "EXTRACTING" || run.status === "COMPLETED" || run.status === "CANCELLED";
  const working = run.status === "QUEUED" || run.status === "DISCOVERING" || run.status === "EXTRACTING";
  return (
    <div className="grid gap-5">
      <div className="grid gap-1">
        <p className="text-sm font-medium" aria-live="polite">
          {STATE_TEXT[run.status]}
          {working ? <AnimatedDots /> : null}
        </p>
        {working ? (
          <p className="flex items-center gap-2 text-xs text-muted-foreground">
            <span className="relative flex size-3 shrink-0 items-center justify-center" aria-hidden="true">
              <span className="absolute inset-0 animate-pulse rounded-full bg-primary/30 motion-reduce:animate-none" />
              <span className="relative size-1.5 rounded-full bg-primary" />
            </span>
            Working – the local model is reading your mail. You can close this window.
          </p>
        ) : null}
      </div>
      {run.status === "FAILED" ? <ErrorMessage message={run.error ?? "Unknown error"} /> : null}
      {run.status === "DISCOVERING" && run.discoverySteps > 0 ? (
        <ProgressBar
          label="Categories"
          value={run.discoveryStepsDone}
          max={run.discoverySteps}
          text={`Step ${run.discoveryStepsDone} of ${run.discoverySteps}`}
        />
      ) : null}
      {run.status === "EXTRACTING" ? <ExtractionStats key={run.id} run={run} updatedAt={updatedAt} /> : null}
      {showEmails && run.status !== "EXTRACTING" ? (
        <ProgressBar
          label="Emails analyzed"
          value={run.processedEmails}
          max={run.totalEmails}
          text={`${run.processedEmails} of ${run.totalEmails} emails`}
          detail={`${percentOf(run.processedEmails, run.totalEmails)}%`}
        />
      ) : null}

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
          <ResultLink href="/findings" icon={ScanSearch} label="Review findings" />
          <ResultLink href="/proposals" icon={Sparkles} label="Review proposals" />
          <ResultLink href="/calendar" icon={CalendarDays} label="Open calendar" />
          <ResultLink href="/tasks" icon={ListTodo} label="Open to-dos" />
        </nav>
      ) : null}
    </div>
  );
}

function percentOf(value: number, max: number): number {
  return max > 0 ? Math.round((value / max) * 100) : 100;
}

/** Live stats while emails are analyzed: which email, how far, what is being read, pace and time left. */
function ExtractionStats({ run, updatedAt }: { run: AnalysisRunDto; updatedAt: number }) {
  // The pace is measured from what this view has seen: the first fetch it showed against the latest one.
  const [first] = useState<ProgressSample>(() => ({ at: updatedAt, processed: run.processedEmails }));
  const pace = secondsPerEmail(first, { at: updatedAt, processed: run.processedEmails });
  const remaining = Math.max(0, run.totalEmails - run.processedEmails);
  const current = Math.min(run.processedEmails + 1, run.totalEmails);

  return (
    <div className="grid gap-3">
      <ProgressBar
        label="Emails analyzed"
        value={run.processedEmails}
        max={run.totalEmails}
        text={`Email ${current} of ${run.totalEmails}`}
        detail={`${percentOf(run.processedEmails, run.totalEmails)}%`}
      />
      {run.currentEmailSubject !== undefined ? (
        <div key={run.currentEmailSubject} className="grid gap-0.5 rounded-lg border bg-muted/30 px-3 py-2 animate-in fade-in slide-in-from-bottom-1 duration-300 motion-reduce:animate-none">
          <span className="text-xs text-muted-foreground">
            Now reading
            <AnimatedDots />
          </span>
          <span className="truncate text-sm font-medium">{run.currentEmailSubject || "(no subject)"}</span>
          {run.currentEmailFrom ? <span className="truncate text-xs text-muted-foreground">{`from ${run.currentEmailFrom}`}</span> : null}
        </div>
      ) : null}
      <p className="text-xs text-muted-foreground">
        {pace === null
          ? "Estimating time left…"
          : `≈ ${Math.round(pace)} s per email · ${formatDuration(pace * remaining)} left`}
      </p>
    </div>
  );
}

function ProgressBar({ label, value, max, text, detail }: { label: string; value: number; max: number; text: string; detail?: string }) {
  const percent = percentOf(value, max);
  return (
    <div className="grid gap-1.5">
      <div
        role="progressbar"
        aria-label={label}
        aria-valuemin={0}
        aria-valuemax={max}
        aria-valuenow={value}
        className="h-2 overflow-hidden rounded-full bg-muted"
      >
        <div
          className="h-full rounded-full bg-primary transition-[width] duration-700 ease-out motion-reduce:transition-none"
          style={{ width: `${percent}%` }}
        />
      </div>
      <span className="flex justify-between text-xs text-muted-foreground">
        <span>{text}</span>
        {detail ? <span className="tabular-nums">{detail}</span> : null}
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
