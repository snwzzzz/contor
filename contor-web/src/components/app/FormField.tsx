import type { ComponentProps } from "react";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

export type FormFieldProps = ComponentProps<typeof Input> & {
  id: string;
  label: string;
  /** Field-level error; marks the input invalid and is read out as its description. */
  error?: string;
  description?: string;
};

/** Labelled text input with accessible error/description wiring. */
export function FormField({ id, label, error, description, ...inputProps }: FormFieldProps) {
  const hint = error ?? description;
  const hintId = hint ? `${id}-hint` : undefined;
  return (
    <div className="grid gap-2">
      <Label htmlFor={id}>{label}</Label>
      <Input id={id} aria-invalid={error ? true : undefined} aria-describedby={hintId} {...inputProps} />
      {hint ? (
        <p id={hintId} className={error ? "text-sm text-destructive" : "text-sm text-muted-foreground"}>
          {hint}
        </p>
      ) : null}
    </div>
  );
}
