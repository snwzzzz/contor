"use client";

import { useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, ArrowRight, Lock } from "lucide-react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useState } from "react";
import { AppButton } from "@/components/app/AppButton";
import { Stepper } from "@/components/app/Stepper";
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
import { useListMailAccounts } from "@/lib/api/generated/endpoints/mail/mail";
import type { MailAccountDto } from "@/lib/api/generated/model/mailAccountDto";
import { AI_SETUP_PARAM } from "@/lib/navigation";
import { cn } from "@/lib/utils";
import { ACCESS_LABEL, AiPermissionsForm, AREAS, historyLabel, permissionsOf, type AiPermissions } from "./AiPermissionsForm";
import { AiModelStatus } from "./AiModelStatus";
import { AnalysisProgress } from "./AnalysisProgress";
import { MailboxAiPicker } from "./MailboxAiPicker";

const STEPS = ["Welcome", "Model", "Permissions", "Mailboxes", "Start", "Analysis"] as const;
const MODEL_STEP = 1;
const PERMISSIONS_STEP = 2;
const MAILBOX_STEP = 3;
const START_STEP = 4;
const ANALYSIS_STEP = STEPS.length - 1;

/** The mailboxes the AI will read: connected, AI-enabled, and not hidden unless the AI reads hidden ones. */
function readMailboxes(accounts: readonly MailAccountDto[] | undefined, readHidden: boolean): MailAccountDto[] {
  return (accounts ?? []).filter((account) => account.status !== "DISABLED" && account.aiEnabled && (!account.hidden || readHidden));
}

/**
 * Step-by-step setup of the AI assistant as a modal over the current page (`?ai-setup=1`): what it does, the local
 * model, the permissions, which mailboxes it reads, then the first inbox analysis. Nothing is read before the user granted mail access here.
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
      <DialogContent className="flex max-h-[min(90svh,48rem)] w-[min(44rem,calc(100%-2rem))] max-w-none flex-col gap-8 overflow-clip sm:max-w-none sm:p-8">
        <div className="grid gap-1.5 pr-8">
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
  const [direction, setDirection] = useState<"forward" | "back">("forward");
  const [draft, setDraft] = useState<AiPermissions | null>(null);
  const { data: settings } = useGetAiSettings();
  const { data: status } = useGetAiStatus();
  const { run } = useLatestAnalysis();
  const { data: accounts } = useListMailAccounts();
  const updateSettings = useUpdateAiSettings();
  const startAnalysis = useStartAnalysis();

  const permissions = draft ?? (settings ? permissionsOf(settings) : null);
  const mailboxes = readMailboxes(accounts, settings?.readHiddenMailboxes ?? true);
  const starting = updateSettings.isPending || startAnalysis.isPending;

  function goTo(next: number) {
    setDirection(next < step ? "back" : "forward");
    setStep(next);
  }

  async function start() {
    if (!permissions) return;
    try {
      const saved = await updateSettings.mutateAsync({ data: { ...permissions, completeOnboarding: true } });
      queryClient.setQueryData(getGetAiSettingsQueryKey(), saved);
      await startAnalysis.mutateAsync();
      await queryClient.invalidateQueries({ queryKey: getGetLatestAnalysisQueryKey() });
      goTo(ANALYSIS_STEP);
    } catch {
      // The mutation error is shown as a toast by the query client; stay on this step.
    }
  }

  const canContinue =
    step === MODEL_STEP
      ? status?.ready === true
      : step === PERMISSIONS_STEP
        ? permissions?.mailAccess === true
        : step === MAILBOX_STEP
          ? mailboxes.length > 0
          : true;

  return (
    <>
      {/* Finished steps go back when clicked; not once the analysis runs (it has started). */}
      <Stepper label="Setup steps" steps={STEPS} current={step} onSelect={step < ANALYSIS_STEP ? goTo : undefined} />

      <div
        key={step}
        className={cn(
          "-mx-1 min-h-0 flex-1 overflow-y-auto px-1 animate-in fade-in duration-300 ease-out motion-reduce:animate-none",
          direction === "forward" ? "slide-in-from-right-6" : "slide-in-from-left-6",
        )}
      >
        {step === 0 ? <WelcomeStep running={isActiveRun(run)} onShowProgress={() => goTo(ANALYSIS_STEP)} /> : null}
        {step === MODEL_STEP ? <AiModelStatus /> : null}
        {step === PERMISSIONS_STEP ? (
          permissions ? (
            <AiPermissionsForm value={permissions} onChange={setDraft} />
          ) : (
            <p className="text-sm text-muted-foreground">Loading…</p>
          )
        ) : null}
        {step === MAILBOX_STEP ? <MailboxStep /> : null}
        {step === START_STEP && permissions ? <Summary permissions={permissions} mailboxes={mailboxes} /> : null}
        {step === ANALYSIS_STEP ? <AnalysisProgress /> : null}
      </div>

      <div className="flex justify-between gap-2">
        {step > 0 && step < ANALYSIS_STEP ? (
          <AppButton variant="ghost" onClick={() => goTo(step - 1)}>
            <ArrowLeft aria-hidden="true" data-icon="inline-start" />
            Back
          </AppButton>
        ) : (
          <span />
        )}
        {step === 0 ? (
          <AppButton onClick={() => goTo(1)}>
            Get started
            <ArrowRight aria-hidden="true" data-icon="inline-end" />
          </AppButton>
        ) : null}
        {step === MODEL_STEP || step === PERMISSIONS_STEP || step === MAILBOX_STEP ? (
          <AppButton disabled={!canContinue} onClick={() => goTo(step + 1)}>
            Next step
            <ArrowRight aria-hidden="true" data-icon="inline-end" />
          </AppButton>
        ) : null}
        {step === START_STEP ? (
          <AppButton loading={starting} onClick={start}>
            Start analysis
            <ArrowRight aria-hidden="true" data-icon="inline-end" />
          </AppButton>
        ) : null}
        {step === ANALYSIS_STEP ? <AppButton onClick={onDone}>Done</AppButton> : null}
      </div>
    </>
  );
}

function WelcomeStep({ running, onShowProgress }: { running: boolean; onShowProgress: () => void }) {
  return (
    <div className="grid gap-6 text-sm leading-relaxed">
      <p>
        The assistant reads your inbox, works out what has happened so far and keeps it organized: the people and organizations you deal with,
        categories such as job applications or orders, its own calendar and its own to-do list. New mail is handled as it arrives.
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

function MailboxStep() {
  return (
    <div className="grid gap-6 text-sm leading-relaxed">
      <p>Choose the mailboxes the assistant reads. The others are never given to the model. You can change this later under Settings → Autonomy.</p>
      <MailboxAiPicker />
    </div>
  );
}

function Summary({ permissions, mailboxes }: { permissions: AiPermissions; mailboxes: readonly MailAccountDto[] }) {
  return (
    <div className="grid gap-6 text-sm leading-relaxed">
      <p>The assistant will analyze your mailboxes with these permissions. You can stop the analysis at any time.</p>
      <dl className="grid grid-cols-[auto_1fr] gap-x-8 gap-y-3">
        <dt className="text-muted-foreground">Mailboxes</dt>
        <dd className="grid gap-0.5">
          {mailboxes.map((mailbox) => (
            <span key={mailbox.id}>{mailbox.emailAddress}</span>
          ))}
        </dd>
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
