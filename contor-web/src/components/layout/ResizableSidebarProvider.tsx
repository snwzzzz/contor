"use client";

import { createContext, useContext, useState, type CSSProperties, type ReactNode } from "react";
import { SidebarProvider } from "@/components/ui/sidebar";
import { useStoredValue, writeStoredValue } from "@/lib/stored-value";
import { cn } from "@/lib/utils";

export const SIDEBAR_WIDTH_KEY = "contor.sidebar.width";
export const SIDEBAR_MIN_WIDTH = 208;
export const SIDEBAR_MAX_WIDTH = 400;
export const SIDEBAR_DEFAULT_WIDTH = 256;

export function clampSidebarWidth(width: number): number {
  return Math.round(Math.min(SIDEBAR_MAX_WIDTH, Math.max(SIDEBAR_MIN_WIDTH, width)));
}

function parseWidth(raw: unknown): number | undefined {
  return typeof raw === "number" && Number.isFinite(raw) ? clampSidebarWidth(raw) : undefined;
}

interface SidebarWidthContextValue {
  width: number;
  /** Live width while dragging (not saved). */
  preview: (width: number) => void;
  /** Saves the width (end of a drag, keyboard). */
  commit: (width: number) => void;
}

const SidebarWidthContext = createContext<SidebarWidthContextValue | null>(null);

export function useSidebarWidth(): SidebarWidthContextValue {
  const context = useContext(SidebarWidthContext);
  if (!context) throw new Error("useSidebarWidth must be used within ResizableSidebarProvider");
  return context;
}

/** SidebarProvider whose width the user can drag; the width is remembered in this browser. */
export function ResizableSidebarProvider({ children }: { children: ReactNode }) {
  const stored = useStoredValue(SIDEBAR_WIDTH_KEY, SIDEBAR_DEFAULT_WIDTH, parseWidth);
  const [dragWidth, setDragWidth] = useState<number | null>(null);
  const width = dragWidth ?? stored;

  const value: SidebarWidthContextValue = {
    width,
    preview: (next) => setDragWidth(clampSidebarWidth(next)),
    commit: (next) => {
      writeStoredValue(SIDEBAR_WIDTH_KEY, clampSidebarWidth(next));
      setDragWidth(null);
    },
  };

  return (
    <SidebarWidthContext.Provider value={value}>
      <SidebarProvider
        style={{ "--sidebar-width": `${width}px` } as CSSProperties}
        // No width animation while dragging, so the sidebar follows the pointer directly.
        className={cn(dragWidth !== null && "[&_[data-slot=sidebar-container]]:transition-none [&_[data-slot=sidebar-gap]]:transition-none select-none")}
      >
        {children}
      </SidebarProvider>
    </SidebarWidthContext.Provider>
  );
}
