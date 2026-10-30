"use client";

import { useState, type ComponentProps } from "react";
import { Input } from "@/components/ui/input";
import { formatClock, parseClock, useTimeFormat } from "@/lib/time-format";
import { cn } from "@/lib/utils";

type TimeInputProps = Omit<ComponentProps<typeof Input>, "type" | "value" | "onChange"> & {
  /** HH:MM (24-hour) or empty. */
  value: string;
  onChange: (value: string) => void;
};

/**
 * A time of day typed as text and shown in the chosen time format (Settings › General); the native time input follows
 * the browser's locale instead. Takes either format while typing ("14:30", "2:30 pm"); every valid time is passed on
 * at once (so Enter submits it), and leaving the field shows it formatted again (an invalid text falls back).
 */
export function TimeInput({ value, onChange, className, onFocus, onBlur, ...props }: TimeInputProps) {
  const format = useTimeFormat();
  // The text being typed; null while the field just shows `value`.
  const [draft, setDraft] = useState<string | null>(null);

  return (
    <Input
      {...props}
      type="text"
      inputMode="text"
      autoComplete="off"
      placeholder={formatClock("09:00", format)}
      value={draft ?? formatClock(value, format)}
      onFocus={(event) => {
        setDraft(formatClock(value, format));
        onFocus?.(event);
      }}
      onChange={(event) => {
        const text = event.target.value;
        setDraft(text);
        if (text.trim() === "") onChange("");
        else {
          const parsed = parseClock(text);
          if (parsed !== null) onChange(parsed);
        }
      }}
      onBlur={(event) => {
        setDraft(null);
        onBlur?.(event);
      }}
      className={cn("w-24 px-2 tabular-nums", className)}
    />
  );
}
