import * as React from "react"
import { ChevronDown } from "lucide-react"
import { cn } from "cn"
import { FIELD, FIELD_FOCUS, FIELD_STATES } from "./input"

// contor: own primitive (not from shadcn). A native <select> in the shared FIELD look with a chevron instead of the
// browser's arrow. Where the browser supports `appearance: base-select` (Chromium), globals.css also styles the
// opened list like our popovers; elsewhere the list stays the system one.
const SIZES = {
  default: "h-8 pl-2.5 pr-8 text-sm",
  /** Dense places (table filters): the field height stays, the text gets smaller. */
  sm: "h-8 pl-2 pr-7 text-xs",
} as const

const VARIANTS = {
  field: cn(FIELD, FIELD_FOCUS),
  /** In a toolbar between ghost buttons: no border until hovered or focused. */
  ghost: "rounded-md hover:bg-accent focus-visible:ring-2 focus-visible:ring-ring",
} as const

interface NativeSelectProps extends Omit<React.ComponentProps<"select">, "size"> {
  size?: keyof typeof SIZES
  variant?: keyof typeof VARIANTS
}

/** `className` sizes the wrapper (e.g. `w-44`); the select fills it. */
function NativeSelect({ className, size = "default", variant = "field", children, ...props }: NativeSelectProps) {
  return (
    <div data-slot="native-select-wrapper" className={cn("relative w-full min-w-0", className)}>
      <select
        data-slot="native-select"
        // No `appearance-none` here: utilities would beat globals.css, which sets `appearance` (none, or base-select).
        className={cn("w-full min-w-0 cursor-pointer truncate", SIZES[size], VARIANTS[variant], FIELD_STATES)}
        {...props}
      >
        {children}
      </select>
      <ChevronDown
        aria-hidden="true"
        className={cn(
          "pointer-events-none absolute top-1/2 -translate-y-1/2 text-muted-foreground",
          size === "sm" ? "right-2 size-3.5" : "right-2.5 size-4",
          props.disabled && "opacity-50"
        )}
      />
    </div>
  )
}

export { NativeSelect }
