import { CHIP_SKELETON, LoadingStatus, widthAt } from "@/components/app/LoadingSkeletons";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";

const CHIP_WIDTHS = ["w-14", "w-24", "w-28", "w-20"] as const;
const CARDS = 6;

/** One `FindingCard`: picture, collection chip and New, the title, a few values, the reasoning, Dismiss and Keep. */
function FindingCardSkeleton({ index }: { index: number }) {
  return (
    <div data-skeleton="card" className="flex h-full flex-col overflow-hidden rounded-xl border bg-card">
      <div className="flex flex-1 flex-col gap-3 p-4">
        <div className="flex items-start gap-3">
          <Skeleton data-skeleton="picture" className="size-10 shrink-0 rounded-full" />
          <div className="grid min-w-0 flex-1 gap-1">
            <div className="flex items-center gap-1.5">
              <Skeleton className="h-5 w-20 rounded-full" />
              <Skeleton className="h-5 w-10 rounded-full" />
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
        <div className="grid grid-cols-[5rem_minmax(0,1fr)] gap-x-3 gap-y-2 py-0.5">
          {Array.from({ length: 2 + (index % 2) }, (_, row) => (
            <div key={row} className="contents">
              <Skeleton className="h-3.5 w-16" />
              <Skeleton className={cn("h-3.5", widthAt(index + row + 1))} />
            </div>
          ))}
        </div>
        <Skeleton className="h-12 w-full rounded-lg" />
      </div>
      <div className="mt-auto flex justify-end gap-2 border-t px-3 py-2">
        <Skeleton data-skeleton="decision" className="h-7 w-20" />
        <Skeleton data-skeleton="decision" className="h-7 w-16" />
      </div>
    </div>
  );
}

/** The findings page below its header: the collection filter chips over the grid of finding cards. */
export function FindingsSkeleton() {
  return (
    <LoadingStatus label="Loading findings" className="grid gap-6">
      <div className="flex flex-wrap gap-1.5">
        {CHIP_WIDTHS.map((width) => (
          <Skeleton key={width} data-skeleton="chip" className={cn(CHIP_SKELETON, width)} />
        ))}
      </div>
      <div className="grid grid-cols-[repeat(auto-fill,minmax(min(100%,20rem),1fr))] gap-4">
        {Array.from({ length: CARDS }, (_, index) => (
          <FindingCardSkeleton key={index} index={index} />
        ))}
      </div>
    </LoadingStatus>
  );
}
