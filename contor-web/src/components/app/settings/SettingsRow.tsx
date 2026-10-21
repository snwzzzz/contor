import type { ReactNode } from "react";
import { FIELD, FIELD_FOCUS, FIELD_STATES } from "@/components/ui/input";
import { cn } from "@/lib/utils";

/** A native select in the shared field look (`components/ui/input.tsx`), sized for a settings row. */
export const SETTINGS_SELECT = cn("h-8 w-44 px-2.5 text-sm", FIELD, FIELD_FOCUS, FIELD_STATES);

interface SettingsRowProps {
  title: string;
  hint: string;
  /** Id of the title, for the control's `aria-labelledby`. */
  labelId: string;
  children: ReactNode;
}

/** One setting as a plain row: label and hint left, the control right; a list of rows is divided by hairlines (`divide-y`). */
export function SettingsRow({ title, hint, labelId, children }: SettingsRowProps) {
  return (
    <div data-slot="settings-row" className="flex flex-wrap items-center justify-between gap-x-8 gap-y-3 py-4">
      <div className="grid min-w-0 gap-0.5">
        <span id={labelId} className="text-sm font-medium">
          {title}
        </span>
        <span className="text-sm text-muted-foreground">{hint}</span>
      </div>
      {children}
    </div>
  );
}
