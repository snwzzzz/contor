"use client";

import { useId } from "react";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import type { CollectionFieldDto } from "@/lib/api/generated/model/collectionFieldDto";
import { formatValue, humanize } from "@/lib/proposal-format";

interface RecordFieldInputProps {
  field: CollectionFieldDto;
  /** The value as the API has it (`null`/missing = empty). */
  value: unknown;
  /** The new value; an emptied field is reported as `null`. */
  onChange: (value: unknown) => void;
  error?: string;
}

/** The choices of an ENUM (`options.values`) or STATUS (`options.states`) field. */
function choicesOf(field: CollectionFieldDto): string[] {
  const list = field.type === "STATUS" ? field.options.states : field.options.values;
  return Array.isArray(list) ? list.filter((choice): choice is string => typeof choice === "string") : [];
}

const SELECT_CLASS =
  "h-9 w-full rounded-lg border border-input bg-transparent px-2.5 text-sm outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 aria-invalid:border-destructive dark:bg-input/30";

/**
 * One labelled input for a collection field, chosen by its type: text, long text, date (ISO), enum/status (select).
 * Other types are shown read-only until they get their own input.
 */
export function RecordFieldInput({ field, value, onChange, error }: RecordFieldInputProps) {
  const id = useId();
  const hintId = error ? `${id}-error` : undefined;
  const text = typeof value === "string" ? value : "";
  const emptyToNull = (next: string) => onChange(next === "" ? null : next);

  let input;
  let editable = true;
  switch (field.type) {
    case "TEXT":
    case "URL":
    case "EMAIL":
    case "PHONE":
      input = <Input id={id} value={text} aria-invalid={error ? true : undefined} aria-describedby={hintId} onChange={(event) => emptyToNull(event.target.value)} />;
      break;
    case "LONG_TEXT":
      input = <Textarea id={id} value={text} aria-invalid={error ? true : undefined} aria-describedby={hintId} onChange={(event) => emptyToNull(event.target.value)} />;
      break;
    case "DATE":
      input = <Input id={id} type="date" value={text} aria-invalid={error ? true : undefined} aria-describedby={hintId} onChange={(event) => emptyToNull(event.target.value)} />;
      break;
    case "ENUM":
    case "STATUS":
      input = (
        <select id={id} value={text} className={SELECT_CLASS} aria-invalid={error ? true : undefined} aria-describedby={hintId} onChange={(event) => emptyToNull(event.target.value)}>
          <option value="">–</option>
          {choicesOf(field).map((choice) => (
            <option key={choice} value={choice}>{humanize(choice)}</option>
          ))}
        </select>
      );
      break;
    default:
      editable = false;
      input = <p className="text-sm">{formatValue(value)}</p>;
  }

  return (
    <div className="grid gap-1.5">
      {editable ? <Label htmlFor={id}>{field.name}</Label> : <span className="text-sm font-medium">{field.name}</span>}
      {input}
      {error ? <p id={hintId} className="text-sm text-destructive">{error}</p> : null}
    </div>
  );
}
