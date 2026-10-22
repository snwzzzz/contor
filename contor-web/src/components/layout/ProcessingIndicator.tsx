"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { AnimatedDots } from "@/components/app/AnimatedDots";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { useGetAiSettings } from "@/lib/api/generated/endpoints/ai/ai";
import type { AnalysisRunDto } from "@/lib/api/generated/model/analysisRunDto";
import { isActiveRun, useLatestAnalysis } from "@/lib/hooks/useLatestAnalysis";
import { aiSetupHref, settingsHref } from "@/lib/navigation";
import { cn } from "@/lib/utils";

function activeText(run: AnalysisRunDto): string {
  switch (run.status) {
    case "EXTRACTING":
      return `AI analyzing ${run.processedEmails}/${run.totalEmails}`;
    case "AWAITING_REVIEW":
      return "AI waiting for review";
    default:
      return "AI looking for categories";
  }
}

/** AI status in the top bar: invites to the setup, shows a running analysis (→ Settings → Autonomy), or that the assistant is active. */
export function ProcessingIndicator() {
  const pathname = usePathname();
  const { data: settings } = useGetAiSettings();
  const { run } = useLatestAnalysis();

  if (!settings) {
    return null;
  }
  const dot = (active: boolean) => (
    <span className={cn("size-2 rounded-full", active ? "animate-pulse bg-primary" : "bg-muted-foreground/50")} aria-hidden="true" />
  );

  if (run && isActiveRun(run)) {
    return (
      <Link href={settingsHref(pathname, "autonomy")} className="flex items-center gap-2 text-xs text-muted-foreground hover:text-foreground">
        {dot(true)}
        <span>
          {activeText(run)}
          <AnimatedDots />
        </span>
      </Link>
    );
  }
  if (!settings.onboardedAt) {
    return (
      <Link href={aiSetupHref(pathname)} className="flex items-center gap-2 text-xs font-medium text-primary hover:underline">
        {dot(false)}
        Set up AI
      </Link>
    );
  }
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <span className="flex items-center gap-2 text-xs text-muted-foreground" tabIndex={0}>
          {dot(false)}
          {settings.mailAccess ? "AI active" : "AI paused"}
        </span>
      </TooltipTrigger>
      <TooltipContent>
        {settings.mailAccess ? "New mail is analyzed in the background." : "Mail access is off (Settings → Autonomy)."}
      </TooltipContent>
    </Tooltip>
  );
}
