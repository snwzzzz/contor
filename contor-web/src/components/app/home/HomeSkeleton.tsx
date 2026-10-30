import { LoadingStatus, widthAt } from "@/components/app/LoadingSkeletons";
import { Skeleton } from "@/components/ui/skeleton";
import { SURFACE_CARD } from "@/lib/card-hover";
import { cn } from "@/lib/utils";

const DESTINATIONS = 6;

interface SectionSkeletonProps {
  rows: number;
  /** Rows start with a small round picture (sender, mailbox). */
  picture?: boolean;
  /** A second, muted line per row. */
  twoLines?: boolean;
  /** A row of tabs under the heading (Recent activity). */
  tabs?: boolean;
  className?: string;
}

/** A `HomeSection` as placeholders: the small heading over its hairline, then rows. */
function SectionSkeleton({ rows, picture = false, twoLines = false, tabs = false, className }: SectionSkeletonProps) {
  return (
    <div data-skeleton="section" className={cn("grid min-w-0 content-start gap-2", className)}>
      <div className="flex h-8 items-center gap-3 border-b">
        <Skeleton className="h-3.5 w-28" />
        <Skeleton className="ml-auto h-3 w-12" />
      </div>
      {tabs ? (
        <div className="flex h-8 items-center gap-4">
          <Skeleton className="h-3 w-12" />
          <Skeleton className="h-3 w-12" />
          <Skeleton className="h-3 w-16" />
        </div>
      ) : null}
      <ul className="grid">
        {Array.from({ length: rows }, (_, index) => (
          <li key={index} className="flex items-center gap-3 py-2">
            {picture ? <Skeleton className="size-6 shrink-0 rounded-full" /> : null}
            <div className="grid min-w-0 flex-1 gap-1.5">
              <Skeleton className={cn("h-4", widthAt(index))} />
              {twoLines ? <Skeleton className="h-3 w-24" /> : null}
            </div>
            <Skeleton className="h-3 w-12 shrink-0" />
          </li>
        ))}
      </ul>
    </div>
  );
}

/**
 * The home page while it loads (`(home)/loading.tsx`): the greeting and status line, the strip of four numbers,
 * Navigate, and the sections in the page's two rows (activity | today + mailboxes, then proposals | AI).
 */
export function HomeSkeleton() {
  return (
    <LoadingStatus label="Loading home" className="mx-auto grid w-full max-w-[85rem] gap-8 py-2 sm:px-2">
      <div data-skeleton="greeting" className="grid gap-1.5">
        <div className="flex h-8 items-center">
          <Skeleton className="h-7 w-72 max-w-full" />
        </div>
        <Skeleton className="h-5 w-80 max-w-full" />
      </div>

      <div className={cn(SURFACE_CARD, "grid gap-px overflow-hidden bg-border sm:grid-cols-2 xl:grid-cols-4")}>
        {Array.from({ length: 4 }, (_, index) => (
          <div key={index} data-skeleton="kpi" className="grid gap-1 bg-surface-raised p-4">
            <div className="flex h-5 items-center justify-between">
              <Skeleton className="h-3.5 w-24" />
              <Skeleton className="size-4" />
            </div>
            <Skeleton className="my-1 h-7 w-12" />
            <Skeleton className="h-4 w-28" />
          </div>
        ))}
      </div>

      <div className="grid gap-2">
        <div className="flex h-8 items-center border-b">
          <Skeleton className="h-3.5 w-16" />
        </div>
        <ul className="-mx-2 grid gap-x-6 sm:grid-cols-2 xl:grid-cols-3">
          {Array.from({ length: DESTINATIONS }, (_, index) => (
            <li key={index} data-skeleton="destination" className="flex items-start gap-3 p-2">
              <Skeleton className="mt-0.5 size-4 shrink-0" />
              <div className="grid min-w-0 flex-1 gap-1.5">
                <Skeleton className="h-4 w-20" />
                <Skeleton className={cn("h-3", widthAt(index))} />
              </div>
            </li>
          ))}
        </ul>
      </div>

      <div className="grid grid-cols-[minmax(0,1fr)] items-start gap-x-10 gap-y-8 lg:grid-cols-[repeat(3,minmax(0,1fr))]">
        <SectionSkeleton rows={6} picture tabs className="lg:col-span-2" />
        <div className="grid min-w-0 grid-cols-[minmax(0,1fr)] gap-8">
          <SectionSkeleton rows={2} />
          <SectionSkeleton rows={2} picture twoLines />
        </div>
        <SectionSkeleton rows={3} twoLines className="lg:col-span-2" />
        <SectionSkeleton rows={3} />
      </div>
    </LoadingStatus>
  );
}
