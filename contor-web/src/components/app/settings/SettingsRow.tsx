import type { ReactNode } from "react";

/** Width of a `NativeSelect` in a settings row. */
export const SETTINGS_SELECT = "w-44";

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
