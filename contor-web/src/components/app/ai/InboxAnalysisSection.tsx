"use client";

import { useQueryClient } from "@tanstack/react-query";
import { AppButton } from "@/components/app/AppButton";
import { SettingsSection } from "@/components/app/settings/SettingsSection";
import { getGetLatestAnalysisQueryKey, useGetAiSettings, useStartAnalysis } from "@/lib/api/generated/endpoints/ai/ai";
import { isActiveRun, useLatestAnalysis } from "@/lib/hooks/useLatestAnalysis";
import { AnalysisProgress } from "./AnalysisProgress";

/** The latest inbox analysis and "Analyze inbox again" (Settings → Autonomy). */
export function InboxAnalysisSection() {
  const queryClient = useQueryClient();
  const { data: settings } = useGetAiSettings();
  const { run } = useLatestAnalysis();
  const start = useStartAnalysis({
    mutation: { onSuccess: () => queryClient.invalidateQueries({ queryKey: getGetLatestAnalysisQueryKey() }) },
  });

  return (
    <SettingsSection title="Inbox analysis">
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
    </SettingsSection>
  );
}
