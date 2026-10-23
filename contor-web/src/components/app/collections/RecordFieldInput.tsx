"use client";

import {
  AlignLeft,
  Banknote,
  Calendar,
  CalendarClock,
  CircleDot,
  ExternalLink,
  Hash,
  Link2,
  List,
  Mail,
  Paperclip,
  Phone,
  ToggleLeft,
  Type,
  type LucideIcon,
} from "lucide-react";
import { useId, type ReactNode } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { NativeSelect } from "@/components/ui/native-select";
import { Textarea } from "@/components/ui/textarea";
import type { CollectionFieldDto } from "@/lib/api/generated/model/collectionFieldDto";
import { TimeInput } from "@/components/app/TimeInput";
import { humanize } from "@/lib/proposal-format";

/** The icon of each field type, next to its name in a property row. */
const TYPE_ICONS: Record<string, LucideIcon> = {
  TEXT: Type,
  LONG_TEXT: AlignLeft,
  URL: ExternalLink,
  EMAIL: Mail,
  PHONE: Phone,
  FILE: Paperclip,
  NUMBER: Hash,
  MONEY: Banknote,
  DATE: Calendar,
  DATETIME: CalendarClock,
  BOOLEAN: ToggleLeft,
  ENUM: List,
  STATUS: CircleDot,
  RELATION: Link2,
};

export function fieldTypeIcon(type: string): LucideIcon {
  return TYPE_ICONS[type] ?? Type;
}

/** The grid of a property row: the name column, then the value. */
export const PROPERTY_ROW = "grid min-w-0 grid-cols-[10rem_minmax(0,1fr)] items-start gap-3";

/** The name cell of a property row: the icon and the muted name, as high as the value's field. */
export function PropertyName({ icon: Icon, htmlFor, children }: { icon: LucideIcon; htmlFor?: string; children: ReactNode }) {
  return (
    <span className="flex h-8 min-w-0 items-center gap-2 text-sm text-muted-foreground">
      <Icon aria-hidden="true" className="size-4 shrink-0" strokeWidth={1.75} />
      {htmlFor ? (
        <Label htmlFor={htmlFor} className="truncate font-normal text-muted-foreground">
          {children}
        </Label>
      ) : (
        <span className="truncate">{children}</span>
      )}
    </span>
  );
}

interface RecordFieldInputProps {
  field: CollectionFieldDto;
  /** The value as the API has it (`null`/missing = empty). */
  value: unknown;
  /** The new value in the API's shape; an emptied field is reported as `null`. */
  onChange: (value: unknown) => void;
  error?: string;
  /** `stacked`: the name above a framed field (forms). `row`: a property row, the value reading as text until edited. */
  layout?: "stacked" | "row";
}

/** The choices of an ENUM (`options.values`) or STATUS (`options.states`) field. */
function choicesOf(field: CollectionFieldDto): string[] {
  const list = field.type === "STATUS" ? field.options.states : field.options.values;
  return Array.isArray(list) ? list.filter((choice): choice is string => typeof choice === "string") : [];
}

function numberOrNull(text: string): number | null {
  if (text.trim() === "") return null;
  const number = Number(text);
  return Number.isFinite(number) ? number : null;
}

/** An ISO instant as a local day (`YYYY-MM-DD`) and time (`HH:MM`); both empty for no or an invalid value. */
function localParts(iso: unknown): { day: string; time: string } {
  const date = typeof iso === "string" && iso !== "" ? new Date(iso) : null;
  if (!date || Number.isNaN(date.getTime())) return { day: "", time: "" };
  const pad = (part: number) => String(part).padStart(2, "0");
  return {
    day: `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`,
    time: `${pad(date.getHours())}:${pad(date.getMinutes())}`,
  };
}

/** A local day and time (midnight without one) as an ISO instant. */
function toInstant(day: string, time: string): string {
  return new Date(`${day}T${time || "00:00"}`).toISOString();
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
export function RecordFieldInput({ field, value, onChange, error, layout = "stacked" }: RecordFieldInputProps) {
  const id = useId();
  const row = layout === "row";
  const variant = row ? "ghost" : "field";
  const hintId = error ? `${id}-error` : undefined;
  const text = typeof value === "string" ? value : "";
  const emptyToNull = (next: string) => onChange(next === "" ? null : next);
  const invalid = error ? true : undefined;
  // In a row an empty value says so, so the field is still visible (and clickable) without its frame.
  const common = { id, variant, placeholder: row ? "Empty" : undefined, "aria-invalid": invalid, "aria-describedby": hintId } as const;

  let input: ReactNode;
  switch (field.type) {
    case "LONG_TEXT":
      input = <Textarea {...common} className={row ? "min-h-8 resize-none" : undefined} value={text} onChange={(event) => emptyToNull(event.target.value)} />;
      break;
    case "DATE":
      input = <Input {...common} type="date" value={text} onChange={(event) => emptyToNull(event.target.value)} />;
      break;
    case "DATETIME": {
      // A day and a time instead of the browser's date-time field, whose time follows the system's locale (AM/PM):
      // the time is typed and shown in the format chosen in Settings. The time needs a day first.
      const parts = localParts(value);
      input = (
        <div className="flex gap-2">
          <Input
            {...common}
            type="date"
            className="min-w-0 flex-1"
            value={parts.day}
            onChange={(event) => onChange(event.target.value === "" ? null : toInstant(event.target.value, parts.time))}
          />
          <TimeInput
            aria-label={`${field.name} time`}
            variant={variant}
            disabled={!parts.day}
            value={parts.time}
            onChange={(time) => onChange(toInstant(parts.day, time))}
          />
        </div>
      );
      break;
    }
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
            variant={variant}
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
        <NativeSelect
          {...common}
          value={value === true ? "yes" : value === false ? "no" : ""}
          onChange={(event) => onChange(event.target.value === "" ? null : event.target.value === "yes")}
        >
          <option value="">–</option>
          <option value="yes">Yes</option>
          <option value="no">No</option>
        </NativeSelect>
      );
      break;
    case "ENUM":
    case "STATUS":
      input = (
        <NativeSelect {...common} value={text} onChange={(event) => emptyToNull(event.target.value)}>
          <option value="">–</option>
          {choicesOf(field).map((choice) => (
            <option key={choice} value={choice}>
              {humanize(choice)}
            </option>
          ))}
        </NativeSelect>
      );
      break;
    default:
      // TEXT, URL, EMAIL, PHONE, FILE: a string.
      input = <Input {...common} value={text} onChange={(event) => emptyToNull(event.target.value)} />;
  }

  const action = actionOf(field, value);
  return (
    <div data-slot={row ? "property-row" : undefined} className={row ? PROPERTY_ROW : "grid min-w-0 gap-1.5"}>
      {row ? (
        <PropertyName icon={fieldTypeIcon(field.type)} htmlFor={id}>
          {field.name}
        </PropertyName>
      ) : (
        <Label htmlFor={id}>{field.name}</Label>
      )}
      {/* In a row the value cell holds the field and its error; stacked, both sit straight in the outer grid. */}
      <div className={row ? "grid min-w-0 gap-1.5" : "contents"}>
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
    </div>
  );
}
