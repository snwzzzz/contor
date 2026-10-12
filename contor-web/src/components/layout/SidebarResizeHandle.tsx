"use client";

import { useRef, type KeyboardEvent, type PointerEvent } from "react";
import { useSidebar } from "@/components/ui/sidebar";
import { SIDEBAR_MAX_WIDTH, SIDEBAR_MIN_WIDTH, useSidebarWidth } from "./ResizableSidebarProvider";

const KEYBOARD_STEP = 16;

/**
 * The sidebar's right edge: drag (or arrow keys) to resize, double-click to collapse. Replaces the shadcn rail while
 * the sidebar is expanded.
 */
export function SidebarResizeHandle() {
  const { width, preview, commit } = useSidebarWidth();
  const { toggleSidebar } = useSidebar();
  const drag = useRef<{ startX: number; startWidth: number; current: number } | null>(null);

  function onPointerDown(event: PointerEvent<HTMLDivElement>) {
    event.preventDefault();
    event.currentTarget.setPointerCapture?.(event.pointerId);
    drag.current = { startX: event.clientX, startWidth: width, current: width };
  }

  function onPointerMove(event: PointerEvent<HTMLDivElement>) {
    if (!drag.current) return;
    drag.current.current = drag.current.startWidth + event.clientX - drag.current.startX;
    preview(drag.current.current);
  }

  function onPointerUp(event: PointerEvent<HTMLDivElement>) {
    if (!drag.current) return;
    event.currentTarget.releasePointerCapture?.(event.pointerId);
    commit(drag.current.current);
    drag.current = null;
  }

  function onKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    const next =
      event.key === "ArrowRight" ? width + KEYBOARD_STEP
        : event.key === "ArrowLeft" ? width - KEYBOARD_STEP
          : event.key === "Home" ? SIDEBAR_MIN_WIDTH
            : event.key === "End" ? SIDEBAR_MAX_WIDTH
              : null;
    if (next === null) return;
    event.preventDefault();
    commit(next);
  }

  return (
    <div
      role="separator"
      aria-orientation="vertical"
      aria-label="Resize sidebar"
      aria-valuemin={SIDEBAR_MIN_WIDTH}
      aria-valuemax={SIDEBAR_MAX_WIDTH}
      aria-valuenow={width}
      tabIndex={0}
      title="Drag to resize, double-click to collapse"
      onPointerDown={onPointerDown}
      onPointerMove={onPointerMove}
      onPointerUp={onPointerUp}
      onPointerCancel={onPointerUp}
      onDoubleClick={toggleSidebar}
      onKeyDown={onKeyDown}
      className="group/resize absolute inset-y-0 -right-2 z-20 hidden w-4 cursor-col-resize touch-none outline-none sm:flex sm:justify-center"
    >
      <span
        aria-hidden="true"
        className="h-full w-0.5 rounded-full bg-transparent transition-colors group-hover/resize:bg-primary/60 group-focus-visible/resize:bg-primary group-active/resize:bg-primary"
      />
    </div>
  );
}
