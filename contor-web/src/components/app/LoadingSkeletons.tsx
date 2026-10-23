import type { ReactNode } from "react";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";

/** Fixed widths (not random) so server and client render the same placeholders. */
const WIDTHS = ["w-3/4", "w-2/3", "w-4/5", "w-1/2", "w-3/5", "w-2/3", "w-3/4", "w-1/2"] as const;
const widthAt = (index: number) => WIDTHS[index % WIDTHS.length];

interface LoadingProps {
  /** What is loading, read by screen readers ("Loading emails"). */
  label: string;
  className?: string;
}

/** A busy status named after what loads; the placeholders inside are decoration. */
function LoadingStatus({ label, className, children }: LoadingProps & { children: ReactNode }) {
  return (
    <div role="status" aria-label={label} aria-busy="true" className={className}>
      {children}
    </div>
  );
}

/** Rows of a list (mail, to-dos, findings, connections): optional avatar, a line, a short date on the right. */
export function LoadingList({ label, className, rows = 6, avatar = false, twoLines = false }: LoadingProps & { rows?: number; avatar?: boolean; twoLines?: boolean }) {
  return (
    <LoadingStatus label={label} className={className}>
      <ul className="grid">
        {Array.from({ length: rows }, (_, index) => (
          <li key={index} className="flex items-center gap-3 border-b py-2.5 last:border-b-0">
            {avatar ? <Skeleton className="size-6 shrink-0 rounded-full" /> : null}
            <div className="grid min-w-0 flex-1 gap-1.5">
              <Skeleton className={cn("h-4", widthAt(index))} />
              {twoLines ? <Skeleton className={cn("h-3", widthAt(index + 3))} /> : null}
            </div>
            <Skeleton className="h-3 w-12 shrink-0" />
          </li>
        ))}
      </ul>
    </LoadingStatus>
  );
}

/** A table: a header row and body rows of cells, the first column a little wider. */
export function LoadingTable({ label, className, rows = 8, columns = 4 }: LoadingProps & { rows?: number; columns?: number }) {
  const row = (key: number, header: boolean) => (
    <div key={key} data-skeleton-row="" className="grid gap-4 border-b px-3 py-3.5" style={{ gridTemplateColumns: `2fr repeat(${columns - 1}, 1fr)` }}>
      {Array.from({ length: columns }, (_, column) => (
        <Skeleton key={column} className={cn(header ? "h-3 w-16" : "h-4", !header && widthAt(key + column))} />
      ))}
    </div>
  );
  return (
    <LoadingStatus label={label} className={className}>
      {row(0, true)}
      {Array.from({ length: rows }, (_, index) => row(index + 1, false))}
    </LoadingStatus>
  );
}

/** A detail view (record, email, report): a title, a meta line, then label/value rows and a paragraph. */
export function LoadingDetail({ label, className, fields = 4 }: LoadingProps & { fields?: number }) {
  return (
    <LoadingStatus label={label} className={cn("grid gap-6", className)}>
      <div className="grid gap-2">
        <Skeleton className="h-7 w-2/5" />
        <Skeleton className="h-4 w-1/4" />
      </div>
      <div className="grid gap-3">
        {Array.from({ length: fields }, (_, index) => (
          <div key={index} className="grid grid-cols-[8rem_minmax(0,1fr)] items-center gap-4">
            <Skeleton className="h-3 w-20" />
            <Skeleton className={cn("h-8", widthAt(index))} />
          </div>
        ))}
      </div>
      <div className="grid gap-2">
        <Skeleton className="h-4 w-full" />
        <Skeleton className="h-4 w-11/12" />
        <Skeleton className="h-4 w-3/5" />
      </div>
    </LoadingStatus>
  );
}

/** A grid of cards (proposals, insights): a title, two lines and an action row each. */
export function LoadingCards({ label, className, count = 4 }: LoadingProps & { count?: number }) {
  return (
    <LoadingStatus label={label} className={className}>
      <ul className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
        {Array.from({ length: count }, (_, index) => (
          <li key={index} className="grid gap-3 rounded-xl border p-4">
            <Skeleton className={cn("h-5", widthAt(index))} />
            <Skeleton className="h-4 w-full" />
            <Skeleton className="h-4 w-4/5" />
            <div className="flex gap-2 pt-1">
              <Skeleton className="h-8 w-20" />
              <Skeleton className="h-8 w-16" />
            </div>
          </li>
        ))}
      </ul>
    </LoadingStatus>
  );
}

/** A settings form: section headings over a hairline, label + field rows. */
export function LoadingForm({ label, className, sections = 2 }: LoadingProps & { sections?: number }) {
  return (
    <LoadingStatus label={label} className={cn("grid gap-10", className)}>
      {Array.from({ length: sections }, (_, section) => (
        <div key={section} className="grid gap-4">
          <Skeleton className="h-4 w-28" />
          <div className="grid gap-2 border-t pt-4">
            <Skeleton className="h-4 w-2/3" />
            <Skeleton className="h-8 w-56" />
          </div>
          <div className="grid gap-2">
            <Skeleton className="h-4 w-1/2" />
            <Skeleton className="h-8 w-56" />
          </div>
        </div>
      ))}
    </LoadingStatus>
  );
}

/** A full page while its route loads: the title line, a toolbar and a list. */
export function LoadingPage({ label = "Loading page" }: Partial<LoadingProps>) {
  return (
    <LoadingStatus label={label} className="grid gap-6 py-2">
      <div className="flex items-center gap-3">
        <Skeleton className="size-5" />
        <Skeleton className="h-6 w-48" />
      </div>
      <Skeleton className="h-9 w-full max-w-md" />
      <LoadingList label="Loading content" rows={8} />
    </LoadingStatus>
  );
}
