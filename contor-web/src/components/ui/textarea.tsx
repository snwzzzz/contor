import * as React from "react"
import { cn } from "cn"
import { FIELD, FIELD_FOCUS, FIELD_STATES } from "@/components/ui/input"

function Textarea({ className, ...props }: React.ComponentProps<"textarea">) {
  return (
    <textarea
      data-slot="textarea"
      className={cn(
        // contor: the shared FIELD look of `input.tsx`.
        "flex field-sizing-content min-h-16 w-full px-2.5 py-2 text-sm",
        FIELD,
        FIELD_FOCUS,
        FIELD_STATES,
        className
      )}
      {...props}
    />
  )
}

export { Textarea }
