"use client";

import { useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { AppButton } from "@/components/app/AppButton";
import { ErrorMessage } from "@/components/app/ErrorMessage";
import { SettingsSection } from "@/components/app/settings/SettingsSection";
import { Sparkles } from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { aiSetupHref } from "@/lib/navigation";
import { getGetAiSettingsQueryKey, useGetAiSettings, useUpdateAiSettings } from "@/lib/api/generated/endpoints/ai/ai";
import { AiPermissionsForm, permissionsOf, type AiPermissions } from "./AiPermissionsForm";
import { InboxAnalysisSection } from "./InboxAnalysisSection";
import { MailboxAiPicker } from "./MailboxAiPicker";
import { LoadingForm } from "@/components/app/LoadingSkeletons";

/**
 * Settings → Autonomy: everything about the assistant except the models. The inbox analysis first (the top-bar
 * progress links here), then the setup wizard, which mailboxes it reads and the permissions (editable at any time).
 */
export function AiAutonomySettings() {
  const queryClient = useQueryClient();
  const pathname = usePathname();
  const { data: settings, isLoading, error } = useGetAiSettings();
  const [draft, setDraft] = useState<AiPermissions | null>(null);
  const save = useUpdateAiSettings({
    mutation: {
      onSuccess: (saved) => {
        queryClient.setQueryData(getGetAiSettingsQueryKey(), saved);
        setDraft(null);
      },
    },
  });

  if (isLoading) {
    return <LoadingForm label="Loading AI settings" sections={3} />;
  }
  if (error || !settings) {
    return <ErrorMessage message={error?.message ?? "Could not load the AI settings."} />;
  }

  const value = draft ?? permissionsOf(settings);
  return (
    <div className="grid gap-10">
      <InboxAnalysisSection />
      <SettingsSection title="Setup">
        <p className="text-sm text-muted-foreground">
          {settings.onboardedAt
            ? "Walk through the setup again: the model, the permissions, the mailboxes and a new inbox analysis."
            : "The assistant is not set up yet. The setup checks the model, asks what it may do and starts the first analysis."}
        </p>
        <Link
          href={aiSetupHref(pathname)}
          className="inline-flex w-fit items-center gap-2 rounded-md border px-3 py-1.5 text-sm hover:bg-accent"
        >
          <Sparkles className="size-4" aria-hidden="true" />
          {settings.onboardedAt ? "Open AI setup" : "Set up the AI assistant"}
        </Link>
      </SettingsSection>
      <SettingsSection title="Mailboxes">
        <p className="text-sm text-muted-foreground">Which mailboxes the AI reads. Changes are saved right away.</p>
        <MailboxAiPicker />
      </SettingsSection>
      <SettingsSection title="Permissions">
        <AiPermissionsForm value={value} onChange={setDraft} />
        <AppButton className="w-fit" disabled={draft === null} loading={save.isPending} onClick={() => save.mutate({ data: value })}>
          Save
        </AppButton>
      </SettingsSection>
    </div>
  );
}
