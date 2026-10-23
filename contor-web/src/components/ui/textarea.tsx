import * as React from "react"
import { cn } from "cn"
import { FIELD_STATES, fieldLook, type FieldVariant } from "@/components/ui/input"

function Textarea({ className, variant = "field", ...props }: React.ComponentProps<"textarea"> & { variant?: FieldVariant }) {
  return (
    <textarea
      data-slot="textarea"
      data-variant={variant}
      className={cn(
        // contor: the shared FIELD look of `input.tsx` (or its ghost).
        "flex field-sizing-content min-h-16 w-full px-2.5 py-2 text-sm",
        fieldLook(variant),
        FIELD_STATES,
        className
      )}
      {...props}
    />
  )
}

export { Textarea }
