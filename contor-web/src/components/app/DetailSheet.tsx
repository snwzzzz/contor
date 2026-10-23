"use client";

import { XIcon, type LucideIcon } from "lucide-react";
import { useEffect, useRef, useState, type CSSProperties, type KeyboardEvent, type ReactNode } from "react";
import { KindLabel } from "./KindLabel";
import { Button } from "@/components/ui/button";
import { Sheet, SheetClose, SheetContent, SheetDescription, SheetFooter, SheetHeader, SheetTitle } from "@/components/ui/sheet";
import type { Tint } from "@/lib/collection-tint";
import { useStoredValue, writeStoredValue } from "@/lib/stored-value";
import { cn } from "@/lib/utils";

/** The width the user dragged the detail sheet to (px), a per-browser preference; `null` = the default (a third). */
const WIDTH_KEY = "contor.detailSheet.width";
const MIN_WIDTH = 320;
const MAX_SHARE = 0.9;
/** One arrow key press on the resize handle. */
const KEY_STEP = 32;

const parseWidth = (raw: unknown) => (typeof raw === "number" && Number.isFinite(raw) ? raw : undefined);

function clampWidth(width: number): number {
  return Math.round(Math.min(Math.max(width, MIN_WIDTH), window.innerWidth * MAX_SHARE));
}

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
  /** Icon buttons in the top right corner, left of the close button and the same size (ghost `icon-sm`), e.g. full screen. */
  actions?: ReactNode;
  /**
   * Where the thing lives (e.g. a link to its collection). With it the sheet has a compact bar instead of the header:
   * the trail, then the title as the last crumb, the actions and close, all in one row; the body shows the rest.
   */
  trail?: ReactNode;
  /** Sticky action bar at the bottom. */
  footer?: ReactNode;
  children: ReactNode;
}

/**
 * The detail sidebar shared by proposals, findings, to-dos and collection reports: header (a small icon with the label, the title), a
 * scrolling body and a sticky footer for the actions. It opens at a third of the window over the page (not dimmed or blurred)
 * and can be resized by dragging its left edge (or with the arrow keys on it; double click resets). Focus trap, Escape
 * and the overlay come from the sheet primitive.
 */
export function DetailSheet({ open, onOpenChange, title, label, icon: Icon, tint, aside, actions, trail, footer, children }: DetailSheetProps) {
  const stored = useStoredValue<number | null>(WIDTH_KEY, null, parseWidth);
  // While dragging, the width follows the pointer; it is saved when the pointer is released.
  const [dragged, setDragged] = useState<number | null>(null);
  const [dragging, setDragging] = useState(false);
  const contentRef = useRef<HTMLDivElement>(null);
  const width = dragged ?? stored;

  useEffect(() => {
    if (!dragging) return;
    let latest: number | null = null;
    function onMove(event: MouseEvent) {
      latest = clampWidth(window.innerWidth - event.clientX);
      setDragged(latest);
    }
    function onUp() {
      if (latest !== null) writeStoredValue(WIDTH_KEY, latest);
      setDragged(null);
      setDragging(false);
    }
    // No text gets selected while the edge is dragged.
    const userSelect = document.body.style.userSelect;
    document.body.style.userSelect = "none";
    window.addEventListener("pointermove", onMove);
    window.addEventListener("pointerup", onUp);
    return () => {
      document.body.style.userSelect = userSelect;
      window.removeEventListener("pointermove", onMove);
      window.removeEventListener("pointerup", onUp);
    };
  }, [dragging]);

  function onHandleKey(event: KeyboardEvent<HTMLDivElement>) {
    if (event.key !== "ArrowLeft" && event.key !== "ArrowRight") return;
    event.preventDefault();
    const current = width ?? contentRef.current?.getBoundingClientRect().width ?? MIN_WIDTH;
    writeStoredValue(WIDTH_KEY, clampWidth(current + (event.key === "ArrowLeft" ? KEY_STEP : -KEY_STEP)));
  }

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent
        ref={contentRef}
        side="right"
        // A clear overlay: the page stays as it is, a click on it still closes the sheet.
        overlayClassName="bg-transparent supports-backdrop-filter:backdrop-blur-none"
        // The bar has its own close button in its row.
        showCloseButton={trail === undefined}
        className={cn(
          // Full width on phones; from sm a third of the window, or the width the user dragged it to.
          "gap-0 data-[side=right]:w-full data-[side=right]:sm:max-w-[90vw]",
          // From sm it floats 8px inside the window: a slight border all around and rounded corners.
          "data-[side=right]:sm:inset-y-2 data-[side=right]:sm:right-2 data-[side=right]:sm:h-auto data-[side=right]:sm:rounded-xl data-[side=right]:sm:border sm:overflow-hidden",
          width === null ? "data-[side=right]:sm:w-[max(33.333vw,24rem)]" : "data-[side=right]:sm:w-(--sheet-width)",
          // No slide transition fighting the pointer while the edge is dragged.
          dragging && "transition-none",
        )}
        style={width !== null ? ({ "--sheet-width": `${width}px` } as CSSProperties) : undefined}
      >
        <div
          role="separator"
          aria-orientation="vertical"
          aria-label="Resize"
          aria-valuenow={width ?? undefined}
          aria-valuemin={MIN_WIDTH}
          tabIndex={0}
          title="Drag to resize, double click to reset"
          onPointerDown={(event) => {
            event.preventDefault();
            setDragging(true);
          }}
          onDoubleClick={() => writeStoredValue(WIDTH_KEY, null)}
          onKeyDown={onHandleKey}
          className={cn(
            "absolute inset-y-0 left-0 z-10 hidden w-2 -translate-x-1/2 cursor-col-resize outline-none sm:block",
            "after:absolute after:inset-y-0 after:left-1/2 after:w-px after:-translate-x-1/2 after:transition-colors",
            "hover:after:bg-ring focus-visible:after:bg-ring",
            dragging && "after:bg-ring",
          )}
        />
        {trail !== undefined ? (
          <div data-slot="sheet-bar" className="flex h-12 shrink-0 items-center gap-1 border-b pr-3 pl-4">
            <nav aria-label="Breadcrumb" className="min-w-0 flex-1">
              <ol className="flex min-w-0 items-center gap-1.5 text-sm text-muted-foreground">
                <li className="shrink-0">{trail}</li>
                <li aria-hidden="true">/</li>
                <li className="min-w-0">
                  <SheetTitle aria-current="page" className="truncate text-sm font-medium text-foreground">
                    {title}
                  </SheetTitle>
                </li>
              </ol>
            </nav>
            <SheetDescription className="sr-only">Details of {title}</SheetDescription>
            {actions}
            <SheetClose asChild>
              <Button variant="ghost" size="icon-sm">
                <XIcon aria-hidden="true" />
                <span className="sr-only">Close</span>
              </Button>
            </SheetClose>
          </div>
        ) : null}
        {/* Pinned like the close button (top-3 right-3, 32px), one button width to its left. */}
        {actions && trail === undefined ? <div className="absolute top-3 right-12 z-10 flex items-center gap-1">{actions}</div> : null}
        {trail !== undefined ? null : (
          <SheetHeader className={cn("shrink-0 border-b", actions ? "pr-24" : "pr-14")}>
            <div className="flex items-start gap-3">
              <div className="grid min-w-0 flex-1 gap-1">
                {/* What kind of thing this is: a small bare icon in its color next to the label, like the page headers. */}
                {Icon ? (
                  <KindLabel icon={Icon} tint={tint}>
                    {label}
                  </KindLabel>
                ) : label ? (
                  <span className="text-xs font-medium text-muted-foreground">{label}</span>
                ) : null}
                <SheetTitle className="leading-snug [overflow-wrap:anywhere]">{title}</SheetTitle>
              </div>
              {aside}
            </div>
            <SheetDescription className="sr-only">Details of {title}</SheetDescription>
          </SheetHeader>
        )}
        {/* Nothing may widen the sheet: long values wrap, a wide mail scrolls in its own frame. */}
        <div className="min-h-0 min-w-0 flex-1 overflow-x-hidden overflow-y-auto p-4">{children}</div>
        {footer ? <SheetFooter className="shrink-0 flex-row justify-end gap-2 border-t">{footer}</SheetFooter> : null}
      </SheetContent>
    </Sheet>
  );
}
