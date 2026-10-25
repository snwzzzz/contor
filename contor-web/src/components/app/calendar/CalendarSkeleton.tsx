import { LoadingStatus } from "@/components/app/LoadingSkeletons";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";

/** The calendar while it loads: the toolbar (title, Week | Month, ‹ Today ›), the week grid's columns and the agenda. */
export function CalendarSkeleton() {
  return (
    <LoadingStatus
      label="Loading calendar"
      className="grid grid-cols-[minmax(0,1fr)] gap-6 lg:min-h-0 lg:flex-1 lg:grid-cols-[minmax(0,1fr)_20rem] lg:grid-rows-[minmax(0,1fr)]"
    >
      <div className="grid gap-3 lg:flex lg:min-h-0 lg:flex-col">
        <div className="flex items-center gap-2">
          <Skeleton className="mr-auto h-7 w-48" />
          <Skeleton data-skeleton="view-switch" className="h-8 w-32 rounded-lg" />
          <Skeleton className="size-8" />
          <Skeleton className="h-8 w-16" />
          <Skeleton className="size-8" />
        </div>
        <div className="grid h-[40rem] grid-cols-[3.5rem_repeat(7,minmax(0,1fr))] overflow-hidden rounded-lg border lg:h-auto lg:min-h-0 lg:flex-1">
          <div />
          {Array.from({ length: 7 }, (_, index) => (
            <div key={index} data-skeleton="day" className="grid content-start gap-3 border-l p-2">
              <Skeleton className="mx-auto h-5 w-12" />
              {index % 3 === 1 ? <Skeleton className="mt-16 h-20 w-full" /> : null}
              {index % 4 === 2 ? <Skeleton className="mt-32 h-10 w-full" /> : null}
            </div>
          ))}
        </div>
      </div>
      <div data-skeleton="agenda" className="grid content-start gap-3">
        <Skeleton className="h-8 w-full" />
        {Array.from({ length: 4 }, (_, index) => (
          <div key={index} className="grid gap-2">
            <Skeleton className="h-7 w-full" />
            <Skeleton className={cn("h-4", index % 2 === 0 ? "w-3/4" : "w-1/2")} />
          </div>
        ))}
      </div>
    </LoadingStatus>
  );
}
