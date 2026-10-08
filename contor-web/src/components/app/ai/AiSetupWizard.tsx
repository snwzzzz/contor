"use client";

import { useQueryClient } from "@tanstack/react-query";
import { Lock } from "lucide-react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useState } from "react";
import { AppButton } from "@/components/app/AppButton";
import { Dialog, DialogContent, DialogDescription, DialogTitle } from "@/components/ui/dialog";
import {
  getGetAiSettingsQueryKey,
  getGetLatestAnalysisQueryKey,
  useGetAiSettings,
  useGetAiStatus,
  useStartAnalysis,
  useUpdateAiSettings,
} from "@/lib/api/generated/endpoints/ai/ai";
import { isActiveRun, useLatestAnalysis } from "@/lib/hooks/useLatestAnalysis";
import { AI_SETUP_PARAM } from "@/lib/navigation";
import { cn } from "@/lib/utils";
import { ACCESS_LABEL, AiPermissionsForm, AREAS, historyLabel, permissionsOf, type AiPermissions } from "./AiPermissionsForm";
import { AiModelStatus } from "./AiModelStatus";
import { AnalysisProgress } from "./AnalysisProgress";

const STEPS = ["Welcome", "Model", "Permissions", "Start", "Analysis"] as const;
const ANALYSIS_STEP = STEPS.length - 1;

/**
 * Step-by-step setup of the AI assistant as a modal over the current page (`?ai-setup=1`): what it does, the local
 * model, the permissions, then the first inbox analysis. Nothing is read before the user granted mail access here.
 */
export function AiSetupWizard() {
  const pathname = usePathname();
  const router = useRouter();
  const searchParams = useSearchParams();
  const open = searchParams.has(AI_SETUP_PARAM);

  function close() {
    const remaining = new URLSearchParams(searchParams.toString());
    remaining.delete(AI_SETUP_PARAM);
    const query = remaining.toString();
    router.replace(query ? `${pathname}?${query}` : pathname, { scroll: false });
  }

  return (
    <Dialog open={open} onOpenChange={(next) => (next ? undefined : close())}>
      <DialogContent className="grid max-h-[min(90svh,48rem)] w-[min(44rem,calc(100%-2rem))] max-w-none grid-rows-[auto_auto_1fr_auto] gap-5 sm:max-w-none">
        <div className="grid gap-1 pr-8">
          <DialogTitle>Set up your AI assistant</DialogTitle>
          <DialogDescription>A local model reads your mail and keeps your contacts, calendar and to-dos.</DialogDescription>
        </div>
        {open ? <WizardSteps onDone={close} /> : null}
      </DialogContent>
    </Dialog>
  );
}

/** The steps; mounted only while the dialog is open, so reopening starts fresh. */
function WizardSteps({ onDone }: { onDone: () => void }) {
  const queryClient = useQueryClient();
  const [step, setStep] = useState(0);
  const [draft, setDraft] = useState<AiPermissions | null>(null);
  const { data: settings } = useGetAiSettings();
  const { data: status } = useGetAiStatus();
  const { run } = useLatestAnalysis();
  const updateSettings = useUpdateAiSettings();
  const startAnalysis = useStartAnalysis();

  const permissions = draft ?? (settings ? permissionsOf(settings) : null);
  const starting = updateSettings.isPending || startAnalysis.isPending;

  async function start() {
    if (!permissions) return;
    try {
      const saved = await updateSettings.mutateAsync({ data: { ...permissions, completeOnboarding: true } });
      queryClient.setQueryData(getGetAiSettingsQueryKey(), saved);
      await startAnalysis.mutateAsync();
      await queryClient.invalidateQueries({ queryKey: getGetLatestAnalysisQueryKey() });
      setStep(ANALYSIS_STEP);
    } catch {
      // The mutation error is shown as a toast by the query client; stay on this step.
    }
  }

  const canContinue = step === 1 ? status?.ready === true : step === 2 ? permissions?.mailAccess === true : true;

  return (
    <>
      <ol aria-label="Setup steps" className="flex flex-wrap gap-2 text-xs">
        {STEPS.map((name, index) => (
          <li
            key={name}
            aria-current={index === step ? "step" : undefined}
            className={cn(
              "rounded-full border px-3 py-1 text-muted-foreground",
              index === step && "border-primary bg-primary text-primary-foreground",
              index < step && "text-foreground",
            )}
          >
            {index + 1}. {name}
          </li>
        ))}
      </ol>

      <div className="min-h-0 overflow-y-auto">
        {step === 0 ? <WelcomeStep running={isActiveRun(run)} onShowProgress={() => setStep(ANALYSIS_STEP)} /> : null}
        {step === 1 ? <AiModelStatus /> : null}
        {step === 2 ? (
          permissions ? <AiPermissionsForm value={permissions} onChange={setDraft} /> : <p className="text-sm text-muted-foreground">Loading…</p>
        ) : null}
        {step === 3 && permissions ? <Summary permissions={permissions} /> : null}
        {step === ANALYSIS_STEP ? <AnalysisProgress /> : null}
      </div>

      <div className="flex justify-between gap-2">
        {step > 0 && step < ANALYSIS_STEP ? (
          <AppButton variant="ghost" onClick={() => setStep(step - 1)}>
            Back
          </AppButton>
        ) : (
          <span />
        )}
        {step === 0 ? <AppButton onClick={() => setStep(1)}>Get started</AppButton> : null}
        {step === 1 || step === 2 ? (
          <AppButton disabled={!canContinue} onClick={() => setStep(step + 1)}>
            Next
          </AppButton>
        ) : null}
        {step === 3 ? (
          <AppButton loading={starting} onClick={start}>
            Start analysis
          </AppButton>
        ) : null}
        {step === ANALYSIS_STEP ? <AppButton onClick={onDone}>Done</AppButton> : null}
      </div>
    </>
  );
}

function WelcomeStep({ running, onShowProgress }: { running: boolean; onShowProgress: () => void }) {
  return (
    <div className="grid gap-4 text-sm">
      <p>
        The assistant reads your inbox, works out what has happened so far and keeps it organized: the people and
        organizations you deal with, categories such as job applications or orders, its own calendar and its own to-do
        list. New mail is handled as it arrives.
      </p>
      <p className="flex items-start gap-2 rounded-lg border bg-muted/40 p-3">
        <Lock className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
        It runs on a local model through Ollama. Your mail never leaves this computer.
      </p>
      <p>Next you choose what it may do. Everything can be changed later under Settings → Autonomy.</p>
      {running ? (
        <AppButton variant="outline" className="w-fit" onClick={onShowProgress}>
          An analysis is running – show progress
        </AppButton>
      ) : null}
    </div>
  );
}

function Summary({ permissions }: { permissions: AiPermissions }) {
  return (
    <div className="grid gap-4 text-sm">
      <p>The assistant will analyze your mailboxes with these permissions. You can stop the analysis at any time.</p>
      <dl className="grid grid-cols-[auto_1fr] gap-x-6 gap-y-2">
        <dt className="text-muted-foreground">History</dt>
        <dd>{historyLabel(permissions.historyDays)}</dd>
        {AREAS.map((area) => (
          <div key={area.key} className="contents">
            <dt className="text-muted-foreground">{area.label}</dt>
            <dd>{ACCESS_LABEL[permissions[area.key]]}</dd>
          </div>
        ))}
      </dl>
    </div>
  );
}
