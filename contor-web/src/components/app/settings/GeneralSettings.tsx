"use client";

import { Monitor, Moon, Sun, type LucideIcon } from "lucide-react";
import { useTheme } from "next-themes";
import { useId, useSyncExternalStore } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { getGetAiSettingsQueryKey, useGetAiSettings, useUpdateAiSettings } from "@/lib/api/generated/endpoints/ai/ai";
import { cn } from "@/lib/utils";
import { SETTINGS_SELECT, SettingsRow } from "./SettingsRow";

const THEMES: readonly { value: string; label: string; icon: LucideIcon }[] = [
  { value: "light", label: "Light", icon: Sun },
  { value: "dark", label: "Dark", icon: Moon },
  { value: "system", label: "System", icon: Monitor },
];

const noSubscribe = () => () => {};

/** False on the server and during hydration: the saved theme is only known in the browser. */
function useMounted(): boolean {
  return useSyncExternalStore(
    noSubscribe,
    () => true,
    () => false,
  );
}

/** The General section of the settings: the color theme (light, dark or the system's), the language and whether the AI reads hidden mailboxes. */
export function GeneralSettings() {
  const { theme, setTheme } = useTheme();
  const mounted = useMounted();
  const themeId = useId();
  const languageId = useId();
  const hiddenId = useId();
  const current = mounted ? (theme ?? "system") : undefined;
  const queryClient = useQueryClient();
  const { data: aiSettings } = useGetAiSettings();
  const updateAi = useUpdateAiSettings({
    mutation: { onSuccess: (saved) => queryClient.setQueryData(getGetAiSettingsQueryKey(), saved) },
  });

  return (
    <div className="grid divide-y">
      <SettingsRow title="Theme" hint="Light, dark, or follow the system." labelId={themeId}>
        {/* Native radios: arrow keys move between them. */}
        <div role="radiogroup" aria-labelledby={themeId} className="flex rounded-lg bg-muted p-0.5">
          {THEMES.map((option) => (
            <label
              key={option.value}
              className={cn(
                // relative: the hidden radio stays inside its label, so focusing it never scrolls the dialog.
                "relative flex h-7 cursor-pointer items-center gap-1.5 rounded-md px-3 text-sm text-muted-foreground hover:text-foreground",
                "has-[:focus-visible]:ring-2 has-[:focus-visible]:ring-ring",
                current === option.value && "bg-background text-foreground shadow-xs",
              )}
            >
              <input
                type="radio"
                name={`${themeId}-theme`}
                value={option.value}
                checked={current === option.value}
                onChange={() => setTheme(option.value)}
                className="sr-only"
              />
              <option.icon aria-hidden="true" className="size-3.5" />
              {option.label}
            </label>
          ))}
        </div>
      </SettingsRow>
      <SettingsRow title="Language" hint="More languages will follow." labelId={languageId}>
        <select aria-labelledby={languageId} value="en" disabled className={SETTINGS_SELECT} onChange={() => undefined}>
          <option value="en">English</option>
        </select>
      </SettingsRow>
      <SettingsRow
        title="AI reads hidden mailboxes"
        hint="Hidden mailboxes (Settings › Accounts) are not shown in contor. By default the AI still reads them."
        labelId={hiddenId}
      >
        <input
          type="checkbox"
          aria-labelledby={hiddenId}
          className="size-4 accent-primary disabled:cursor-not-allowed disabled:opacity-50"
          checked={aiSettings?.readHiddenMailboxes ?? true}
          disabled={aiSettings === undefined || updateAi.isPending}
          onChange={(event) => updateAi.mutate({ data: { readHiddenMailboxes: event.target.checked } })}
        />
      </SettingsRow>
    </div>
  );
}
