"use client";

import { useId } from "react";
import { Textarea } from "@/components/ui/textarea";
import type { AiAccess } from "@/lib/api/generated/model/aiAccess";
import type { AiSettingsDto } from "@/lib/api/generated/model/aiSettingsDto";

/** What the AI may do; reading hidden mailboxes is a General setting, saved on its own. */
export type AiPermissions = Omit<AiSettingsDto, "onboardedAt" | "readHiddenMailboxes">;
type AreaKey = "categories" | "records" | "calendar" | "tasks" | "replies";

/** The editable permissions (everything except the onboarding timestamp and the hidden-mailbox setting). */
export function permissionsOf(settings: AiSettingsDto): AiPermissions {
  const { mailAccess, historyDays, categories, records, calendar, tasks, replies, courtesyReplies, ownerProfile } = settings;
  return { mailAccess, historyDays, categories, records, calendar, tasks, replies, courtesyReplies, ownerProfile };
}

export const ACCESS_LABEL: Record<AiAccess, string> = { OFF: "Off", SUGGEST: "Ask me", AUTO: "Automatic" };
const LEVELS: readonly AiAccess[] = ["OFF", "SUGGEST", "AUTO"];
/** A reply draft is never sent, so "Automatic" would have nothing to do. */
const REPLY_LEVELS: readonly AiAccess[] = ["OFF", "SUGGEST"];

export const AREAS: readonly { key: AreaKey; label: string; description: string; levels: readonly AiAccess[] }[] = [
  { key: "categories", label: "New categories", description: "Create collections for what your inbox is about, e.g. job applications or orders.", levels: LEVELS },
  { key: "records", label: "Contacts & records", description: "Add and update people, organizations and entries in your collections.", levels: LEVELS },
  { key: "calendar", label: "Calendar", description: "Put appointments, interviews, deadlines and trips into the assistant's calendar.", levels: LEVELS },
  { key: "tasks", label: "To-dos", description: "Turn requests in emails into to-dos with due dates.", levels: LEVELS },
  {
    key: "replies",
    label: "Reply drafts",
    description: "Write a reply for emails that need an answer from you. Drafts are never sent; you copy or open them in your mail app.",
    levels: REPLY_LEVELS,
  },
];

/** The "All areas" preset: every area at one level; reply drafts are never sent, so they stop at "Ask me". */
function withAllAreas(value: AiPermissions, level: AiAccess): AiPermissions {
  const replies = level === "AUTO" ? "SUGGEST" : level;
  return { ...value, categories: level, records: level, calendar: level, tasks: level, replies };
}

/** The preset the areas match, or `null` when they are mixed. */
function allAreasLevel(value: AiPermissions): AiAccess | null {
  return LEVELS.find((level) => AREAS.every((area) => value[area.key] === withAllAreas(value, level)[area.key])) ?? null;
}

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

/** What the AI may do: the mail-access master switch, the history window and Off / Ask me / Automatic per area (reply drafts: Off / Ask me). */
export function AiPermissionsForm({ value, onChange }: AiPermissionsFormProps) {
  const historyId = useId();
  const profileId = useId();
  return (
    <div className="grid gap-6">
      <label className="flex items-start gap-3">
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

      {/* While mail access is off only the inputs fade; labels and hints stay as they are. */}
      <div className="grid gap-2">
        <label htmlFor={historyId} className="text-sm font-medium">
          How far back should the first analysis read?
        </label>
        <select
          id={historyId}
          className="h-9 w-56 rounded-md border bg-background px-3 text-sm disabled:cursor-not-allowed disabled:opacity-50"
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

      <div className="grid gap-2">
        <label htmlFor={profileId} className="text-sm font-medium">
          About you and your business
        </label>
        <Textarea
          id={profileId}
          className="min-h-20"
          maxLength={2000}
          disabled={!value.mailAccess}
          placeholder="e.g. Head of Operations at a wholesaler: customers order from us, we buy from suppliers."
          value={value.ownerProfile ?? ""}
          onChange={(event) => onChange({ ...value, ownerProfile: event.target.value })}
        />
        <span className="text-sm text-muted-foreground">
          Optional. Tells the assistant whose side an email is on, e.g. that a customer&apos;s order is an order you receive.
        </span>
      </div>

      <div className="grid gap-4">
        <AccessLevelPicker
          label="All areas"
          description="Set every area below at once."
          levels={LEVELS}
          value={allAreasLevel(value)}
          disabled={!value.mailAccess}
          onChange={(level) => onChange(withAllAreas(value, level))}
        />
        {AREAS.map((area) => (
          <AccessLevelPicker
            key={area.key}
            label={area.label}
            description={area.description}
            levels={area.levels}
            value={value[area.key]}
            disabled={!value.mailAccess}
            onChange={(level) => onChange({ ...value, [area.key]: level })}
          />
        ))}
        <label className="flex items-start gap-3 text-sm">
          <input
            type="checkbox"
            className="mt-0.5 size-4 accent-primary disabled:cursor-not-allowed disabled:opacity-50"
            checked={value.courtesyReplies}
            disabled={!value.mailAccess || value.replies === "OFF"}
            onChange={(event) => onChange({ ...value, courtesyReplies: event.target.checked })}
          />
          <span className="grid gap-0.5">
            <span>Also suggest polite replies to emails that need no answer</span>
            <span className="text-muted-foreground">
              A thank-you after a job rejection, after help, or congratulations: marked optional and never urgent.
            </span>
          </span>
        </label>
      </div>
    </div>
  );
}

interface AccessLevelPickerProps {
  label: string;
  description: string;
  levels: readonly AiAccess[];
  /** `null`: none checked (the "All areas" preset when the areas are mixed). */
  value: AiAccess | null;
  disabled: boolean;
  onChange: (value: AiAccess) => void;
}

function AccessLevelPicker({ label, description, levels, value, disabled, onChange }: AccessLevelPickerProps) {
  const id = useId();
  return (
    // Choices always to the right of the label; a long description wraps instead of pushing them below.
    <div className="grid grid-cols-[minmax(0,1fr)_auto] items-center gap-x-6 gap-y-3">
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
        className="inline-flex rounded-md border p-0.5 has-disabled:opacity-50"
      >
        {levels.map((level) => (
          <label
            key={level}
            className="relative cursor-pointer rounded px-3 py-1 text-sm has-checked:bg-primary has-checked:text-primary-foreground has-disabled:cursor-not-allowed has-focus-visible:ring-2 has-focus-visible:ring-ring"
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
