"use client";

import { useQueryClient } from "@tanstack/react-query";
import { Sparkles } from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { AppButton } from "@/components/app/AppButton";
import { getGetLatestAnalysisQueryKey, useGetAiSettings, useStartAnalysis } from "@/lib/api/generated/endpoints/ai/ai";
import { isActiveRun, useLatestAnalysis } from "@/lib/hooks/useLatestAnalysis";
import { aiSetupHref } from "@/lib/navigation";
import { AiModelStatus } from "./AiModelStatus";
import { AnalysisProgress } from "./AnalysisProgress";

/** Settings → AI Models: the local model, the latest analysis, and the way into the setup wizard. */
export function AiSettingsPanel() {
  const pathname = usePathname();
  const queryClient = useQueryClient();
  const { data: settings } = useGetAiSettings();
  const { run } = useLatestAnalysis();
  const start = useStartAnalysis({
    mutation: { onSuccess: () => queryClient.invalidateQueries({ queryKey: getGetLatestAnalysisQueryKey() }) },
  });

  return (
    <div className="grid gap-8">
      <section className="grid gap-3">
        <h3 className="text-sm font-semibold">Local model</h3>
        <AiModelStatus />
      </section>

      <section className="grid gap-3">
        <h3 className="text-sm font-semibold">Inbox analysis</h3>
        {settings?.onboardedAt ? (
          <>
            <AnalysisProgress />
            {!isActiveRun(run) && settings.mailAccess ? (
              <AppButton variant="outline" className="w-fit" loading={start.isPending} onClick={() => start.mutate()}>
                Analyze inbox again
              </AppButton>
            ) : null}
          </>
        ) : (
          <p className="text-sm text-muted-foreground">The assistant is not set up yet.</p>
        )}
        <Link
          href={aiSetupHref(pathname)}
          className="inline-flex w-fit items-center gap-2 rounded-md border px-3 py-1.5 text-sm hover:bg-accent"
        >
          <Sparkles className="size-4" aria-hidden="true" />
          {settings?.onboardedAt ? "Open AI setup" : "Set up the AI assistant"}
        </Link>
      </section>
    </div>
  );
}
