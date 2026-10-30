import { LoadingStatus, TrailSkeleton, widthAt } from "@/components/app/LoadingSkeletons";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";

const SECTIONS = 3;
const SOURCES = 4;

/**
 * A report until it is there, laid out like `ReportView`: breadcrumbs (not when `embedded` in the record's Reports
 * tab), the header with title, meta line and Regenerate/Delete over its hairline, then the question bubble and the
 * answer's sections next to the sources column.
 */
export function ReportSkeleton({ embedded = false }: { embedded?: boolean }) {
  return (
    <LoadingStatus label="Loading report" className={cn("grid gap-6", embedded ? "min-w-0" : "mx-auto w-full max-w-6xl")}>
      {embedded ? null : <TrailSkeleton closable />}
      <div data-skeleton="report-header" className="grid gap-2 border-b pb-5">
        <div className={cn("flex items-center", embedded ? "h-7" : "h-8")}>
          <Skeleton className={cn("max-w-full", embedded ? "h-5 w-64" : "h-7 w-80")} />
        </div>
        <div className="flex h-4 items-center gap-4">
          <Skeleton className="h-3 w-32" />
          <Skeleton className="h-3 w-16" />
          <Skeleton className="h-3 w-24" />
        </div>
        <div className="flex items-center gap-2 pt-1">
          <Skeleton className="h-7 w-28" />
          <Skeleton className="h-7 w-20" />
        </div>
      </div>
      <div className="grid gap-8 lg:grid-cols-[minmax(0,1fr)_20rem]">
        <div className="grid min-w-0 content-start gap-8">
          <div data-skeleton="question" className="flex justify-end">
            <Skeleton className="h-10 w-2/3 max-w-[85%] rounded-2xl rounded-tr-sm" />
          </div>
          {Array.from({ length: SECTIONS }, (_, section) => (
            <div key={section} data-skeleton="answer-section" className="grid gap-3">
              <Skeleton className="h-5 w-48" />
              {Array.from({ length: 3 }, (_, line) => (
                <Skeleton key={line} className={cn("h-4", line === 2 ? widthAt(section) : "w-full")} />
              ))}
            </div>
          ))}
        </div>
        <div className="grid min-w-0 content-start gap-2">
          <Skeleton className="h-4 w-16" />
          <div className="grid gap-1.5">
            {Array.from({ length: SOURCES }, (_, index) => (
              <div key={index} data-skeleton="source" className="flex items-start gap-2 rounded-lg border px-3 py-2">
                <Skeleton className="size-5 shrink-0" />
                <div className="grid min-w-0 flex-1 gap-1.5 py-0.5">
                  <Skeleton className={cn("h-3.5", widthAt(index))} />
                  <Skeleton className="h-3 w-1/2" />
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </LoadingStatus>
  );
}
