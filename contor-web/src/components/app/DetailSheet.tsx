"use client";

import type { LucideIcon } from "lucide-react";
import type { ReactNode } from "react";
import { Sheet, SheetContent, SheetDescription, SheetFooter, SheetHeader, SheetTitle } from "@/components/ui/sheet";
import { tintClass, type Tint } from "@/lib/collection-tint";
import { cn } from "@/lib/utils";

interface DetailSheetProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  /** Small heading above the title (what kind of thing this is). */
  label?: string;
  icon?: LucideIcon;
  tint?: Tint;
  /** Content right of the title (e.g. a confidence bar). */
  aside?: ReactNode;
  /** Sticky action bar at the bottom. */
  footer?: ReactNode;
  children: ReactNode;
}

/**
 * The detail sidebar shared by proposals and to-dos: header (icon, label, title), a scrolling body and a sticky footer
 * for the actions. Focus trap, Escape and the overlay come from the sheet primitive.
 */
export function DetailSheet({ open, onOpenChange, title, label, icon: Icon, tint, aside, footer, children }: DetailSheetProps) {
  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent side="right" className="gap-0 sm:max-w-lg">
        <SheetHeader className="shrink-0 border-b pr-14">
          <div className="flex items-start gap-3">
            {Icon ? (
              <span className={cn("flex size-9 shrink-0 items-center justify-center rounded-lg", tint ? tintClass(tint) : "bg-muted")} aria-hidden="true">
                <Icon className="size-4" />
              </span>
            ) : null}
            <div className="grid min-w-0 flex-1 gap-0.5">
              {label ? <span className="text-xs font-medium text-muted-foreground">{label}</span> : null}
              <SheetTitle className="leading-snug break-words">{title}</SheetTitle>
            </div>
            {aside}
          </div>
          <SheetDescription className="sr-only">Details of {title}</SheetDescription>
        </SheetHeader>
        <div className="min-h-0 flex-1 overflow-y-auto p-4">{children}</div>
        {footer ? <SheetFooter className="shrink-0 flex-row justify-end gap-2 border-t">{footer}</SheetFooter> : null}
      </SheetContent>
    </Sheet>
  );
}
