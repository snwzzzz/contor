"use client";

import { useId } from "react";
import type { AiAccess } from "@/lib/api/generated/model/aiAccess";
import type { AiSettingsDto } from "@/lib/api/generated/model/aiSettingsDto";

export type AiPermissions = Omit<AiSettingsDto, "onboardedAt">;
type AreaKey = "categories" | "records" | "calendar" | "tasks";

/** The editable part of the settings (everything except the onboarding timestamp). */
export function permissionsOf(settings: AiSettingsDto): AiPermissions {
  const { mailAccess, historyDays, categories, records, calendar, tasks } = settings;
  return { mailAccess, historyDays, categories, records, calendar, tasks };
}

export const ACCESS_LABEL: Record<AiAccess, string> = { OFF: "Off", SUGGEST: "Ask me", AUTO: "Automatic" };
const LEVELS: readonly AiAccess[] = ["OFF", "SUGGEST", "AUTO"];

export const AREAS: readonly { key: AreaKey; label: string; description: string }[] = [
  { key: "categories", label: "New categories", description: "Create collections for what your inbox is about, e.g. job applications or orders." },
  { key: "records", label: "Contacts & records", description: "Add and update people, organizations and entries in your collections." },
  { key: "calendar", label: "Calendar", description: "Put appointments, interviews, deadlines and trips into the assistant's calendar." },
  { key: "tasks", label: "To-dos", description: "Turn requests in emails into to-dos with due dates." },
];

export const HISTORY_OPTIONS: readonly { days: number; label: string }[] = [
  { days: 30, label: "Last 30 days" },
  { days: 90, label: "Last 90 days" },
  { days: 365, label: "Last year" },
  { days: 0, label: "Everything" },
];

export function historyLabel(days: number): string {
  return HISTORY_OPTIONS.find((option) => option.days === days)?.label ?? `Last ${days} days`;
}

interface AiPermissionsFormProps {
  value: AiPermissions;
  onChange: (value: AiPermissions) => void;
}

/** What the AI may do: the mail-access master switch, the history window and Off / Ask me / Automatic per area. */
export function AiPermissionsForm({ value, onChange }: AiPermissionsFormProps) {
  const historyId = useId();
  return (
    <div className="grid gap-6">
      <label className="flex items-start gap-3 rounded-lg border p-4">
        <input
          type="checkbox"
          className="mt-1 size-4 accent-primary"
          checked={value.mailAccess}
          onChange={(event) => onChange({ ...value, mailAccess: event.target.checked })}
        />
        <span className="grid gap-1">
          <span className="font-medium">Read and analyze my mailboxes</span>
          <span className="text-sm text-muted-foreground">
            The model runs on this computer; nothing is sent anywhere. Without this the assistant does nothing.
          </span>
        </span>
      </label>

      <div className="grid gap-2">
        <label htmlFor={historyId} className="text-sm font-medium">
          How far back should the first analysis read?
        </label>
        <select
          id={historyId}
          className="h-9 w-56 rounded-md border bg-background px-3 text-sm"
          value={value.historyDays}
          disabled={!value.mailAccess}
          onChange={(event) => onChange({ ...value, historyDays: Number(event.target.value) })}
        >
          {HISTORY_OPTIONS.map((option) => (
            <option key={option.days} value={option.days}>
              {option.label}
            </option>
          ))}
        </select>
      </div>

      <div className="grid gap-4">
        {AREAS.map((area) => (
          <AccessLevelPicker
            key={area.key}
            label={area.label}
            description={area.description}
            value={value[area.key]}
            disabled={!value.mailAccess}
            onChange={(level) => onChange({ ...value, [area.key]: level })}
          />
        ))}
      </div>
    </div>
  );
}

interface AccessLevelPickerProps {
  label: string;
  description: string;
  value: AiAccess;
  disabled: boolean;
  onChange: (value: AiAccess) => void;
}

function AccessLevelPicker({ label, description, value, disabled, onChange }: AccessLevelPickerProps) {
  const id = useId();
  return (
    <div className="flex flex-wrap items-center justify-between gap-3">
      <div className="grid gap-0.5">
        <span id={`${id}-label`} className="text-sm font-medium">
          {label}
        </span>
        <span id={`${id}-description`} className="max-w-md text-sm text-muted-foreground">
          {description}
        </span>
      </div>
      <div
        role="radiogroup"
        aria-labelledby={`${id}-label`}
        aria-describedby={`${id}-description`}
        className="inline-flex rounded-md border p-0.5"
      >
        {LEVELS.map((level) => (
          <label
            key={level}
            className="cursor-pointer rounded px-3 py-1 text-sm has-checked:bg-primary has-checked:text-primary-foreground has-disabled:cursor-not-allowed has-disabled:opacity-50 has-focus-visible:ring-2 has-focus-visible:ring-ring"
          >
            <input
              type="radio"
              className="sr-only"
              name={id}
              value={level}
              checked={value === level}
              disabled={disabled}
              onChange={() => onChange(level)}
            />
            {ACCESS_LABEL[level]}
          </label>
        ))}
      </div>
    </div>
  );
}
