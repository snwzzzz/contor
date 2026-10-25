import { CHIP_SKELETON, LoadingStatus, widthAt } from "@/components/app/LoadingSkeletons";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";

const CARDS = 6;

/** A row of filter chips, `FilterChip`'s height. */
function Chips({ widths }: { widths: readonly string[] }) {
  return (
    <div className="flex flex-wrap gap-1.5">
      {widths.map((width, index) => (
        <Skeleton key={index} className={cn(CHIP_SKELETON, width)} />
      ))}
    </div>
  );
}

/** One `ActionCard`: kind label, the title, a few values, the reasoning, Reject and Approve. */
function ProposalCardSkeleton({ index }: { index: number }) {
  return (
    <div data-skeleton="card" className="flex h-full flex-col overflow-hidden rounded-xl border bg-card">
      <div className="flex-1 pb-4">
        <div className="flex items-start gap-3 p-4 pb-3">
          <div className="grid min-w-0 flex-1 gap-1">
            <div data-skeleton="kind" className="flex h-4 items-center gap-1.5">
              <Skeleton className="size-3.5" />
              <Skeleton className="h-3 w-28" />
            </div>
            <div className="flex h-6 items-center">
              <Skeleton className={cn("h-4", widthAt(index))} />
            </div>
          </div>
          <div className="grid shrink-0 justify-items-end gap-1">
            <Skeleton className="h-3 w-7" />
            <Skeleton className="h-1 w-10 rounded-full" />
          </div>
        </div>
        <div className="grid grid-cols-[5rem_minmax(0,1fr)] gap-x-3 gap-y-2 px-4 py-0.5">
          {Array.from({ length: 2 + (index % 3 === 0 ? 1 : 0) }, (_, row) => (
            <div key={row} className="contents">
              <Skeleton className="h-3.5 w-16" />
              <Skeleton className={cn("h-3.5", widthAt(index + row + 2))} />
            </div>
          ))}
        </div>
        <div className="mx-4 mt-3 grid gap-1.5">
          <Skeleton className="h-3 w-full" />
          <Skeleton className="h-3 w-4/5" />
        </div>
      </div>
      <div className="mt-auto flex justify-end gap-2 border-t px-3 py-2">
        <Skeleton data-skeleton="decision" className="h-7 w-16" />
        <Skeleton data-skeleton="decision" className="h-7 w-20" />
      </div>
    </div>
  );
}

/**
 * The proposals page below its header: the count with "Look for insights" and the kind chips, the importance and
 * order chips, then the grid of proposal cards.
 */
export function ProposalsSkeleton() {
  return (
    <LoadingStatus label="Loading proposals" className="grid gap-4">
      <div data-skeleton="toolbar" className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex flex-wrap items-center gap-3">
          <Skeleton className="h-4 w-44" />
          <Skeleton className="h-7 w-32" />
        </div>
        <Chips widths={["w-14", "w-24", "w-20", "w-24"]} />
      </div>
      <div data-skeleton="toolbar" className="flex flex-wrap items-center justify-between gap-3">
        <Chips widths={["w-24", "w-16"]} />
        <Chips widths={["w-24", "w-36"]} />
      </div>
      <div className="grid grid-cols-[repeat(auto-fill,minmax(min(100%,22rem),1fr))] gap-4">
        {Array.from({ length: CARDS }, (_, index) => (
          <ProposalCardSkeleton key={index} index={index} />
        ))}
      </div>
    </LoadingStatus>
  );
}
