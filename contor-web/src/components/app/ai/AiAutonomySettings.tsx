"use client";

import { useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { AppButton } from "@/components/app/AppButton";
import { ErrorMessage } from "@/components/app/ErrorMessage";
import { getGetAiSettingsQueryKey, useGetAiSettings, useUpdateAiSettings } from "@/lib/api/generated/endpoints/ai/ai";
import { AiPermissionsForm, permissionsOf, type AiPermissions } from "./AiPermissionsForm";

/** Settings → Autonomy: the same permissions as in the setup wizard, editable at any time. */
export function AiAutonomySettings() {
  const queryClient = useQueryClient();
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
    return <p className="text-sm text-muted-foreground">Loading…</p>;
  }
  if (error || !settings) {
    return <ErrorMessage message={error?.message ?? "Could not load the AI settings."} />;
  }

  const value = draft ?? permissionsOf(settings);
  return (
    <div className="grid gap-6">
      <AiPermissionsForm value={value} onChange={setDraft} />
      <AppButton className="w-fit" disabled={draft === null} loading={save.isPending} onClick={() => save.mutate({ data: value })}>
        Save
      </AppButton>
    </div>
  );
}
