"use client";

import { ExternalLink, Mail, Phone, type LucideIcon } from "lucide-react";
import { useId, type ReactNode } from "react";
import { Button } from "@/components/ui/button";
import { FIELD, FIELD_FOCUS, FIELD_STATES, Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import type { CollectionFieldDto } from "@/lib/api/generated/model/collectionFieldDto";
import { humanize } from "@/lib/proposal-format";
import { cn } from "@/lib/utils";

interface RecordFieldInputProps {
  field: CollectionFieldDto;
  /** The value as the API has it (`null`/missing = empty). */
  value: unknown;
  /** The new value in the API's shape; an emptied field is reported as `null`. */
  onChange: (value: unknown) => void;
  error?: string;
}

/** The choices of an ENUM (`options.values`) or STATUS (`options.states`) field. */
function choicesOf(field: CollectionFieldDto): string[] {
  const list = field.type === "STATUS" ? field.options.states : field.options.values;
  return Array.isArray(list) ? list.filter((choice): choice is string => typeof choice === "string") : [];
}

/** A native select in the shared field look (`components/ui/input.tsx`). */
const SELECT_CLASS = cn("h-8 w-full px-2.5 text-sm", FIELD, FIELD_FOCUS, FIELD_STATES);

function numberOrNull(text: string): number | null {
  if (text.trim() === "") return null;
  const number = Number(text);
  return Number.isFinite(number) ? number : null;
}

/** An ISO instant as the value of a `datetime-local` input (the user's local time, minutes). */
function toLocalInput(iso: unknown): string {
  if (typeof iso !== "string" || iso === "") return "";
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return "";
  const pad = (part: number) => String(part).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

interface Money {
  amount: number;
  currency: string;
}

function moneyOf(value: unknown): Money | null {
  if (value && typeof value === "object" && "amount" in value && typeof value.amount === "number") {
    const currency = "currency" in value && typeof value.currency === "string" ? value.currency : "EUR";
    return { amount: value.amount, currency };
  }
  return null;
}

/** Where an email address, phone number or link leads, for the small open button next to its input. */
function actionOf(field: CollectionFieldDto, value: unknown): { href: string; label: string; icon: LucideIcon; external: boolean } | null {
  if (typeof value !== "string" || value.trim() === "") return null;
  if (field.type === "EMAIL") return { href: `mailto:${value}`, label: `Send email to ${value}`, icon: Mail, external: false };
  if (field.type === "PHONE") return { href: `tel:${value.replace(/[^\d+]/g, "")}`, label: `Call ${value}`, icon: Phone, external: false };
  if (field.type === "URL") return { href: value, label: `Open ${value}`, icon: ExternalLink, external: true };
  return null;
}

/**
 * One labelled input for a collection field, chosen by its type, in the shared field look. Every type is editable:
 * text-like fields, long text, number, money (amount + currency), date, date and time, yes/no, choices, the id of a
 * related record and a file name. Email addresses, phone numbers and links get a small button that opens them.
 */
export function RecordFieldInput({ field, value, onChange, error }: RecordFieldInputProps) {
  const id = useId();
  const hintId = error ? `${id}-error` : undefined;
  const text = typeof value === "string" ? value : "";
  const emptyToNull = (next: string) => onChange(next === "" ? null : next);
  const invalid = error ? true : undefined;
  const common = { id, "aria-invalid": invalid, "aria-describedby": hintId } as const;

  let input: ReactNode;
  switch (field.type) {
    case "LONG_TEXT":
      input = <Textarea {...common} value={text} onChange={(event) => emptyToNull(event.target.value)} />;
      break;
    case "DATE":
      input = <Input {...common} type="date" value={text} onChange={(event) => emptyToNull(event.target.value)} />;
      break;
    case "DATETIME":
      input = (
        <Input
          {...common}
          type="datetime-local"
          value={toLocalInput(value)}
          onChange={(event) => onChange(event.target.value === "" ? null : new Date(event.target.value).toISOString())}
        />
      );
      break;
    case "NUMBER":
    case "RELATION":
      input = (
        <Input
          {...common}
          type="number"
          step={field.type === "RELATION" ? 1 : "any"}
          value={typeof value === "number" ? String(value) : ""}
          onChange={(event) => onChange(numberOrNull(event.target.value))}
        />
      );
      break;
    case "MONEY": {
      const money = moneyOf(value);
      input = (
        <div className="flex gap-2">
          <Input
            {...common}
            type="number"
            step="0.01"
            value={money ? String(money.amount) : ""}
            onChange={(event) => {
              const amount = numberOrNull(event.target.value);
              onChange(amount === null ? null : { amount, currency: money?.currency ?? "EUR" });
            }}
          />
          <Input
            aria-label={`${field.name} currency`}
            className="w-20 uppercase"
            maxLength={3}
            value={money?.currency ?? "EUR"}
            disabled={money === null}
            onChange={(event) => money && onChange({ amount: money.amount, currency: event.target.value.toUpperCase() })}
          />
        </div>
      );
      break;
    }
    case "BOOLEAN":
      input = (
        <select
          {...common}
          className={SELECT_CLASS}
          value={value === true ? "yes" : value === false ? "no" : ""}
          onChange={(event) => onChange(event.target.value === "" ? null : event.target.value === "yes")}
        >
          <option value="">–</option>
          <option value="yes">Yes</option>
          <option value="no">No</option>
        </select>
      );
      break;
    case "ENUM":
    case "STATUS":
      input = (
        <select {...common} value={text} className={SELECT_CLASS} onChange={(event) => emptyToNull(event.target.value)}>
          <option value="">–</option>
          {choicesOf(field).map((choice) => (
            <option key={choice} value={choice}>
              {humanize(choice)}
            </option>
          ))}
        </select>
      );
      break;
    default:
      // TEXT, URL, EMAIL, PHONE, FILE: a string.
      input = <Input {...common} value={text} onChange={(event) => emptyToNull(event.target.value)} />;
  }

  const action = actionOf(field, value);
  return (
    <div className="grid min-w-0 gap-1.5">
      <Label htmlFor={id}>{field.name}</Label>
      {/* The same wrapper with or without the open button, so emptying the field never remounts (and unfocuses) it. */}
      <div className="flex min-w-0 items-center gap-1">
        <div className="min-w-0 flex-1">{input}</div>
        {action ? (
          <Button asChild variant="ghost" size="icon-sm" className="shrink-0 text-muted-foreground">
            <a href={action.href} aria-label={action.label} title={action.label} {...(action.external ? { target: "_blank", rel: "noreferrer" } : {})}>
              <action.icon aria-hidden="true" />
            </a>
          </Button>
        ) : null}
      </div>
      {error ? (
        <p id={hintId} className="text-sm text-destructive">
          {error}
        </p>
      ) : null}
    </div>
  );
}
