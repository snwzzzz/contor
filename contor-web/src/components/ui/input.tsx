import * as React from "react"
import { cn } from "cn"

// contor: the one field look of the whole app (inputs, textareas, search fields and field-like boxes such as the
// report composer or the address chips). Change it here, never per use.
export const FIELD =
  "rounded-md border border-input bg-background shadow-none transition-[border-color,box-shadow] dark:bg-input/20"
// contor: focus is a darker border only, no glow around the field.
export const FIELD_FOCUS = "focus-visible:border-ring"
/** For a box that holds a borderless control (chips + input, composer). */
export const FIELD_FOCUS_WITHIN = "focus-within:border-ring"
const FIELD_STATES =
  "outline-none placeholder:text-muted-foreground disabled:pointer-events-none disabled:cursor-not-allowed disabled:opacity-50 aria-invalid:border-destructive"

function Input({ className, type, ...props }: React.ComponentProps<"input">) {
  return (
    <input
      type={type}
      data-slot="input"
      className={cn(
        // contor: shared FIELD look, h-8 and text-sm everywhere.
        "h-8 w-full min-w-0 px-2.5 py-1 text-sm file:inline-flex file:h-6 file:border-0 file:bg-transparent file:text-sm file:font-medium file:text-foreground",
        FIELD,
        FIELD_FOCUS,
        FIELD_STATES,
        className
      )}
      {...props}
    />
  )
}

export { Input, FIELD_STATES }
