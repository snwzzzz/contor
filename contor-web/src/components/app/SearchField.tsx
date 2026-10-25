"use client";

import { Search, X } from "lucide-react";
import type { ComponentProps, ReactNode } from "react";
import { Input } from "@/components/ui/input";
import { cn } from "@/lib/utils";

interface SearchFieldProps extends Omit<ComponentProps<typeof Input>, "type"> {
  /** Shows a Clear (✕) button while there is text. */
  onClear?: () => void;
  /** Extra controls at the right end inside the field (e.g. a scope menu); reserve room with `className` padding. */
  trailing?: ReactNode;
  /** Classes of the wrapper (width, flex). */
  wrapperClassName?: string;
}

/** The one search box of the app: search icon, the shared field look, an optional Clear and trailing controls. */
export function SearchField({ onClear, trailing, wrapperClassName, className, value, ...props }: SearchFieldProps) {
  const clearable = onClear !== undefined && typeof value === "string" && value !== "";
  return (
    <div className={cn("relative min-w-0", wrapperClassName)}>
      <Search aria-hidden="true" className="pointer-events-none absolute top-1/2 left-2.5 size-4 -translate-y-1/2 text-muted-foreground" />
      <Input
        type="search"
        value={value}
        // The browser's own clear button would differ per browser; Clear is ours.
        className={cn("pl-8 [&::-webkit-search-cancel-button]:appearance-none", clearable && !trailing && "pr-8", className)}
        {...props}
      />
      {clearable || trailing ? (
        <div className="absolute top-1/2 right-1 flex -translate-y-1/2 items-center gap-0.5">
          {clearable ? (
            <button
              type="button"
              aria-label="Clear search"
              onClick={onClear}
              className="flex size-6 items-center justify-center rounded text-muted-foreground outline-none hover:text-foreground focus-visible:ring-2 focus-visible:ring-ring"
            >
              <X aria-hidden="true" className="size-3.5" />
            </button>
          ) : null}
          {trailing}
        </div>
      ) : null}
    </div>
  );
}
