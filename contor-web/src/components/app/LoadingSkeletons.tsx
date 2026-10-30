import type { ReactNode } from "react";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";

/** Fixed widths (not random) so server and client render the same placeholders. */
const WIDTHS = ["w-3/4", "w-2/3", "w-4/5", "w-1/2", "w-3/5", "w-2/3", "w-3/4", "w-1/2"] as const;
export const widthAt = (index: number) => WIDTHS[index % WIDTHS.length];

interface LoadingProps {
  /** What is loading, read by screen readers ("Loading emails"). */
  label: string;
  className?: string;
}

/** A busy status named after what loads; the placeholders inside are decoration. */
export function LoadingStatus({ label, className, children }: LoadingProps & { children: ReactNode }) {
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

/** A `FilterChip` (text-xs, py-1 and a border): as high as the real one. */
export const CHIP_SKELETON = "h-[1.625rem] rounded-full";

/** A whole page's status:header and content stacked with the gap `<main>` puts between them. */
export const PAGE_STATUS = "flex flex-col gap-5";
/** The same for pages that are exactly the screen's height (inbox, calendar, a collection's table). */
export const FULL_PAGE_STATUS = "flex min-h-0 flex-1 flex-col gap-5";

const COMMAND_WIDTHS =["w-20", "w-24", "w-20", "w-28"] as const;

/** The breadcrumbs line of `PageTrail` (same height), as placeholders; `closable` adds its Close at the end. */
export function TrailSkeleton({ closable = false, className }: { closable?: boolean; className?: string }) {
  return (
    <div data-skeleton="trail" className={cn("flex h-7 items-center gap-2", className)}>
      <Skeleton className="h-3 w-10" />
      <Skeleton className="h-3 w-20" />
      {closable ? <Skeleton className="ml-auto size-7" /> : null}
    </div>
  );
}

interface PageHeaderSkeletonProps {
  /** How many commands the page's bar has. */
  commands?: number;
  /** The section icon next to the title. */
  icon?: boolean;
  description?: boolean;
  /** The title's width class. */
  titleWidth?: string;
}

/**
 * `PageHeader` as placeholders, row for row at the same heights (breadcrumbs, icon + title + commands + Close, the
 * description), so the real header replaces it without a jump. Decoration only: the page's status names what loads.
 */
export function PageHeaderSkeleton({ commands = 3, icon = true, description = true, titleWidth = "w-40" }: PageHeaderSkeletonProps) {
  return (
    <div data-skeleton="page-header" aria-hidden="true" className="grid gap-1">
      <TrailSkeleton />
      <div className="flex min-w-0 items-center gap-4">
        <div className="flex h-8 shrink-0 items-center gap-2.5">
          {icon ? <Skeleton data-skeleton="page-icon" className="size-6" /> : null}
          <Skeleton className={cn("h-7", titleWidth)} />
        </div>
        {commands > 0 ? (
          <div className="flex min-w-0 flex-1 items-center gap-1 overflow-hidden border-l pl-3">
            {Array.from({ length: commands }, (_, index) => (
              <Skeleton key={index} data-skeleton="command" className={cn("h-7 shrink-0 max-md:w-7", COMMAND_WIDTHS[index % COMMAND_WIDTHS.length])} />
            ))}
          </div>
        ) : (
          <div className="flex-1" />
        )}
        <Skeleton className="size-8 shrink-0" />
      </div>
      {description ? (
        <div data-skeleton="description" className="flex h-5 items-center">
          <Skeleton className="h-3.5 w-full max-w-md" />
        </div>
      ) : null}
    </div>
  );
}

/** A page that is its header and an `EmptyState` (pages still to come): the icon and its two centered lines. */
export function PlaceholderPageSkeleton({ label, commands = 2, icon = true }: { label: string; commands?: number; icon?: boolean }) {
  return (
    <LoadingStatus label={label} className={PAGE_STATUS}>
      <PageHeaderSkeleton commands={commands} icon={icon} />
      <div data-skeleton="empty-state" className="flex flex-col items-center justify-center gap-4 px-6 py-16">
        <Skeleton className="size-9" />
        <div className="grid w-full max-w-md justify-items-center gap-2">
          <Skeleton className="h-5 w-48" />
          <Skeleton className="h-4 w-72 max-w-full" />
        </div>
      </div>
    </LoadingStatus>
  );
}
