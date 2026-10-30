"use client";

import { Plus, X } from "lucide-react";
import { useId } from "react";
import { AppButton } from "@/components/app/AppButton";
import { FormField } from "@/components/app/FormField";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { NativeSelect } from "@/components/ui/native-select";
import { Textarea } from "@/components/ui/textarea";
import type { ApiAuthType } from "@/lib/api/generated/model/apiAuthType";
import {
  AUTH_FIELD_LABELS,
  AUTH_KINDS,
  INTERVAL_PRESETS,
  intervalLabel,
  newRowId,
  type AppDraft,
  type FieldErrors,
  type HeaderRow,
} from "@/lib/external-apis";
import { cn } from "@/lib/utils";

interface DraftProps {
  draft: AppDraft;
  onChange: (draft: AppDraft) => void;
  errors: FieldErrors;
}

/** Step 1: what the app is and where it lives. */
export function BasicsFields({ draft, onChange, errors }: DraftProps) {
  const descriptionId = useId();
  return (
    <div className="grid gap-5">
      <FormField
        id="app-name"
        label="Name"
        value={draft.name}
        maxLength={80}
        error={errors.name}
        placeholder="My shop"
        onChange={(event) => onChange({ ...draft, name: event.target.value })}
      />
      <FormField
        id="app-base-url"
        label="Base URL"
        value={draft.baseUrl}
        error={errors.baseUrl}
        description="HTTPS. The paths of the endpoints are added to it."
        placeholder="https://api.example.com/v1"
        inputMode="url"
        onChange={(event) => onChange({ ...draft, baseUrl: event.target.value })}
      />
      <FormField
        id="app-docs-url"
        label="Documentation link"
        value={draft.docsUrl}
        error={errors.docsUrl}
        description="Optional. Later the assistant reads it to suggest endpoints."
        placeholder="https://docs.example.com"
        inputMode="url"
        onChange={(event) => onChange({ ...draft, docsUrl: event.target.value })}
      />
      <div className="grid gap-2">
        <Label htmlFor={descriptionId}>Description</Label>
        <Textarea
          id={descriptionId}
          value={draft.description}
          maxLength={1000}
          placeholder="What it holds and why it matters, in a sentence or two."
          onChange={(event) => onChange({ ...draft, description: event.target.value })}
        />
      </div>
    </div>
  );
}

interface AuthFieldsProps extends DraftProps {
  /** A secret is stored for the saved app (edit mode). */
  hasStoredSecret?: boolean;
}

/** Step 2: how contor signs in, and headers sent with every call. Secrets are write-only. */
export function AuthFields({ draft, onChange, errors, hasStoredSecret = false }: AuthFieldsProps) {
  const kind = AUTH_KINDS[draft.authKind];
  return (
    <div className="grid gap-8">
      <div className="grid gap-5">
        <div className="grid gap-2">
          <Label htmlFor="app-auth-kind">Authentication</Label>
          <NativeSelect
            id="app-auth-kind"
            className="w-64"
            value={draft.authKind}
            onChange={(event) => onChange({ ...draft, authKind: event.target.value as ApiAuthType })}
          >
            {(Object.keys(AUTH_KINDS) as ApiAuthType[]).map((value) => (
              <option key={value} value={value}>
                {AUTH_KINDS[value].label}
              </option>
            ))}
          </NativeSelect>
        </div>
        {kind.fields.map((field) => (
          <FormField
            key={field}
            id={`app-auth-${field}`}
            label={field === "scope" ? `${AUTH_FIELD_LABELS[field]} (optional)` : AUTH_FIELD_LABELS[field]}
            value={draft.authConfig[field]}
            error={errors[field]}
            autoComplete="off"
            onChange={(event) => onChange({ ...draft, authConfig: { ...draft.authConfig, [field]: event.target.value } })}
          />
        ))}
        {kind.secretLabel ? (
          draft.secret === null ? (
            <div className="grid gap-2">
              <span className="text-sm font-medium">{kind.secretLabel}</span>
              <div className="flex items-center gap-3">
                <span className="text-sm text-muted-foreground">
                  <span aria-hidden="true">•••• </span>
                  <span>{kind.secretLabel} stored</span>
                </span>
                <AppButton variant="outline" size="xs" onClick={() => onChange({ ...draft, secret: "" })}>
                  Replace
                </AppButton>
              </div>
            </div>
          ) : (
            <div className="grid gap-2">
              <FormField
                id="app-secret"
                label={kind.secretLabel}
                type="password"
                autoComplete="new-password"
                value={draft.secret}
                error={errors.secret}
                description="Stored encrypted. It is never shown again."
                onChange={(event) => onChange({ ...draft, secret: event.target.value })}
              />
              {hasStoredSecret ? (
                <AppButton variant="ghost" size="xs" className="w-fit" onClick={() => onChange({ ...draft, secret: null })}>
                  Keep the stored one
                </AppButton>
              ) : null}
            </div>
          )
        ) : null}
      </div>

      <section aria-labelledby="app-headers" className="grid gap-3">
        <div className="grid gap-0.5">
          <h3 id="app-headers" className="text-sm font-medium">
            Headers
          </h3>
          <p className="text-sm text-muted-foreground">Sent with every call. Secret ones are stored encrypted and never shown again.</p>
        </div>
        <KeyValueRows
          rows={draft.headers}
          onChange={(headers) => onChange({ ...draft, headers })}
          errors={errors}
          errorPrefix="header"
          label="Header"
          addLabel="Add header"
          allowSecret
        />
      </section>
    </div>
  );
}

interface KeyValueRowsProps {
  rows: HeaderRow[];
  onChange: (rows: HeaderRow[]) => void;
  errors: FieldErrors;
  /** Errors are keyed `<prefix>.<rowId>`. */
  errorPrefix: string;
  /** "Header" → "Header 1 name". */
  label: string;
  addLabel: string;
  allowSecret?: boolean;
}

/** Name/value rows (headers, query parameters); a secret row hides its value, a stored one keeps it until replaced. */
export function KeyValueRows({ rows, onChange, errors, errorPrefix, label, addLabel, allowSecret = false }: KeyValueRowsProps) {
  function update(id: string, change: Partial<HeaderRow>) {
    onChange(rows.map((row) => (row.id === id ? { ...row, ...change } : row)));
  }

  return (
    <div className="grid gap-2">
      {rows.length > 0 ? (
        <ul className="grid gap-2">
          {rows.map((row, index) => {
            const number = index + 1;
            const error = errors[`${errorPrefix}.${row.id}`];
            const errorId = error ? `${errorPrefix}-${row.id}-error` : undefined;
            return (
              <li key={row.id} className="grid gap-1">
                <div className={cn("grid items-center gap-2", allowSecret ? "grid-cols-[minmax(0,1fr)_minmax(0,1.4fr)_auto_auto]" : "grid-cols-[minmax(0,1fr)_minmax(0,1.4fr)_auto]")}>
                  <Input
                    aria-label={`${label} ${number} name`}
                    aria-invalid={error ? true : undefined}
                    aria-describedby={errorId}
                    placeholder="Name"
                    value={row.name}
                    autoComplete="off"
                    // A renamed stored header is a new one: it needs its value.
                    onChange={(event) => update(row.id, { name: event.target.value, stored: false })}
                  />
                  <Input
                    aria-label={`${label} ${number} value`}
                    aria-describedby={errorId}
                    type={row.secret ? "password" : "text"}
                    autoComplete={row.secret ? "new-password" : "off"}
                    placeholder={row.stored ? "•••• stored, type to replace" : "Value"}
                    value={row.value}
                    onChange={(event) => update(row.id, { value: event.target.value })}
                  />
                  {allowSecret ? (
                    <label className="flex items-center gap-1.5 text-xs text-muted-foreground">
                      <input
                        type="checkbox"
                        className="size-4 accent-primary"
                        aria-label={`${label} ${number} is secret`}
                        checked={row.secret}
                        onChange={(event) => update(row.id, { secret: event.target.checked, stored: false })}
                      />
                      <span aria-hidden="true">Secret</span>
                    </label>
                  ) : null}
                  <AppButton
                    variant="ghost"
                    size="icon-xs"
                    aria-label={`Remove ${label.toLowerCase()} ${number}`}
                    onClick={() => onChange(rows.filter((entry) => entry.id !== row.id))}
                  >
                    <X aria-hidden="true" />
                  </AppButton>
                </div>
                {error ? (
                  <p id={errorId} className="text-sm text-destructive">
                    {error}
                  </p>
                ) : null}
              </li>
            );
          })}
        </ul>
      ) : null}
      <AppButton
        variant="outline"
        size="xs"
        className="w-fit"
        onClick={() => onChange([...rows, { id: newRowId(), name: "", value: "", secret: false, stored: false }])}
      >
        <Plus aria-hidden="true" data-icon="inline-start" />
        {addLabel}
      </AppButton>
    </div>
  );
}

const SEGMENT = cn(
  "relative flex h-7 cursor-pointer items-center rounded-md px-3 text-sm text-muted-foreground hover:text-foreground",
  "has-[:focus-visible]:ring-2 has-[:focus-visible]:ring-ring",
);
const SEGMENT_CHECKED = "bg-background text-foreground shadow-xs";

/** Last step: how often contor calls the app and what the assistant may do with the answers. */
export function ScheduleFields({ draft, onChange }: Omit<DraftProps, "errors">) {
  const intervalId = useId();
  const presets: { minutes: number; label: string }[] = [...INTERVAL_PRESETS];
  if (!presets.some((preset) => preset.minutes === draft.pollIntervalMinutes)) {
    presets.push({ minutes: draft.pollIntervalMinutes, label: intervalLabel(draft.pollIntervalMinutes) });
  }
  return (
    <div className="grid gap-6">
      <div className="grid gap-2">
        <span id={intervalId} className="text-sm font-medium">
          How often
        </span>
        <div role="radiogroup" aria-labelledby={intervalId} className="flex w-fit flex-wrap rounded-lg bg-muted p-0.5">
          {presets.map((preset) => (
            <label key={preset.minutes} className={cn(SEGMENT, draft.pollIntervalMinutes === preset.minutes && SEGMENT_CHECKED)}>
              <input
                type="radio"
                name={`${intervalId}-interval`}
                value={preset.minutes}
                checked={draft.pollIntervalMinutes === preset.minutes}
                onChange={() => onChange({ ...draft, pollIntervalMinutes: preset.minutes })}
                className="sr-only"
              />
              {preset.label}
            </label>
          ))}
        </div>
        <p className="text-sm text-muted-foreground">
          Each enabled endpoint is called once per interval. contor only reads: it sends GET requests and the POST queries you marked.
        </p>
      </div>
      <label className="flex items-start gap-3">
        <input
          type="checkbox"
          className="mt-0.5 size-4 accent-primary"
          checked={draft.enabled}
          onChange={(event) => onChange({ ...draft, enabled: event.target.checked })}
        />
        <span className="grid gap-0.5">
          <span className="text-sm font-medium">Call it on schedule</span>
          <span className="text-sm text-muted-foreground">Off: the app stays saved but is not called.</span>
        </span>
      </label>
      <label className="flex items-start gap-3">
        <input
          type="checkbox"
          className="mt-0.5 size-4 accent-primary"
          checked={draft.aiAnalysis}
          onChange={(event) => onChange({ ...draft, aiAnalysis: event.target.checked })}
        />
        <span className="grid gap-0.5">
          <span className="text-sm font-medium">Let the assistant learn from it</span>
          <span className="text-sm text-muted-foreground">
            The local model reads the answers and suggests records and insights, following your autonomy settings.
          </span>
        </span>
      </label>
    </div>
  );
}
