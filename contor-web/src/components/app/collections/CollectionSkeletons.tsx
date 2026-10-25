import { CHIP_SKELETON, FULL_PAGE_STATUS, LoadingStatus, PageHeaderSkeleton, TrailSkeleton, widthAt } from "@/components/app/LoadingSkeletons";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";

const OVERVIEW_ROWS = 6;
const CHIP_WIDTHS = ["w-12", "w-20", "w-16", "w-32"] as const;

/** The collections overview below its header: search and origin chips with the count, then the table of collections. */
export function CollectionsOverviewSkeleton() {
  return (
    <LoadingStatus label="Loading collections" className="grid gap-3">
      <div className="flex flex-wrap items-center gap-3">
        <Skeleton data-skeleton="search" className="h-8 w-full max-w-xs" />
        <div className="flex flex-wrap gap-1.5">
          {CHIP_WIDTHS.map((width) => (
            <Skeleton key={width} data-skeleton="chip" className={cn(CHIP_SKELETON, width)} />
          ))}
        </div>
        <Skeleton className="ml-auto h-3 w-20" />
      </div>
      <table aria-hidden="true" className="w-full table-fixed text-sm">
        <thead>
          <tr data-skeleton="table-head" className="border-b">
            <th className="py-2 pr-4"><Skeleton className="h-3 w-12" /></th>
            <th className="w-24 py-2 pr-4"><Skeleton className="ml-auto h-3 w-14" /></th>
            <th className="hidden w-20 py-2 pr-4 md:table-cell"><Skeleton className="ml-auto h-3 w-10" /></th>
            <th className="hidden w-40 py-2 md:table-cell"><Skeleton className="h-3 w-12" /></th>
          </tr>
        </thead>
        <tbody>
          {Array.from({ length: OVERVIEW_ROWS }, (_, index) => (
            <tr key={index} data-skeleton="row" className="border-b last:border-b-0">
              <td className="py-2.5 pr-4">
                <div className="flex items-start gap-3">
                  <Skeleton className="mt-0.5 size-4 shrink-0" />
                  <div className="grid min-w-0 flex-1 gap-1.5 py-0.5">
                    <Skeleton className={cn("h-3.5", index % 2 === 0 ? "w-36" : "w-28")} />
                    <Skeleton className={cn("h-3 max-w-80", widthAt(index))} />
                  </div>
                </div>
              </td>
              <td className="py-2.5 pr-4"><Skeleton className="ml-auto h-4 w-8" /></td>
              <td className="hidden py-2.5 pr-4 md:table-cell"><Skeleton className="ml-auto h-4 w-6" /></td>
              <td className="hidden py-2.5 md:table-cell"><Skeleton className="h-4 w-20" /></td>
            </tr>
          ))}
        </tbody>
      </table>
    </LoadingStatus>
  );
}

/**
 * A collection's page until its definition is there: the page header, the table's toolbar (search, rows per page,
 * columns, reports, filters) and the table with its header and rows, so the real table replaces it in place.
 */
export function CollectionTableSkeleton({ rows = 12, columns = 4 }: { rows?: number; columns?: number }) {
  return (
    <LoadingStatus label="Loading collection" className={FULL_PAGE_STATUS}>
      <PageHeaderSkeleton commands={4} />
      <div className="flex min-h-0 flex-1 flex-col gap-2">
        <div className="flex flex-wrap items-center gap-2">
          <Skeleton data-skeleton="search" className="h-8 w-full max-w-xs" />
          <div className="ml-auto flex items-center gap-1">
            <Skeleton className="h-9 w-20" />
            <Skeleton className="h-9 w-24" />
            <Skeleton className="h-9 w-24" />
            <Skeleton className="size-9" />
          </div>
        </div>
        <div className="min-h-0 flex-1 overflow-hidden">
          <table aria-hidden="true" className="w-full border-separate border-spacing-0 text-sm">
            <thead>
              <tr data-skeleton="table-head">
                {Array.from({ length: columns }, (_, column) => (
                  <th key={column} className="h-9 border-b border-l px-3 first:border-l-0">
                    <Skeleton className={cn("h-3", column === 0 ? "w-12" : "w-16")} />
                  </th>
                ))}
                <th className="w-10 border-b px-1" />
              </tr>
            </thead>
            <tbody>
              {Array.from({ length: rows }, (_, row) => (
                <tr key={row} data-skeleton="row">
                  {Array.from({ length: columns }, (_, column) => (
                    <td key={column} className="border-b border-l px-3 py-3.5 first:border-l-0">
                      <Skeleton className={cn("h-5", widthAt(row + column))} />
                    </td>
                  ))}
                  <td className="border-b" />
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </LoadingStatus>
  );
}

const PROPERTIES = 6;
const TABS = ["w-16", "w-16", "w-20", "w-24"] as const;

/**
 * A record until it is there: on its page the breadcrumbs, the 64px picture next to title | collection with the
 * commands, the quick actions, the four tabs and the property rows of Details. In the sidebar (`sheet`) the same with
 * the 48px picture and the closer tabs, without breadcrumbs and commands.
 */
export function RecordSkeleton({ variant = "page" }: { variant?: "page" | "sheet" }) {
  const sheet = variant === "sheet";
  const picture = sheet ? 48 : 64;
  return (
    <LoadingStatus label="Loading record" className="grid gap-3">
      {sheet ? null : <TrailSkeleton closable />}
      <div className="grid gap-4">
        <div className={cn("flex flex-wrap items-center gap-y-2", sheet ? "gap-x-3" : "gap-x-4")}>
          <Skeleton data-skeleton="picture" className="shrink-0 rounded-lg" style={{ width: picture, height: picture }} />
          <div className="grid min-w-0 flex-1 gap-0.5">
            <div className={cn("flex items-center", sheet ? "h-7" : "h-8")}>
              <Skeleton className={cn(sheet ? "h-5 w-40" : "h-7 w-56", "max-w-full")} />
            </div>
            <div className="flex h-5 items-center">
              <Skeleton className="h-3.5 w-36" />
            </div>
          </div>
          {sheet ? null : (
            <div className="flex items-center gap-1">
              <Skeleton className="h-7 w-20" />
              <Skeleton className="h-7 w-24" />
            </div>
          )}
        </div>
        <div className="flex gap-2">
          <Skeleton data-skeleton="quick-action" className="h-7 w-16" />
          <Skeleton data-skeleton="quick-action" className="h-7 w-14" />
          <Skeleton data-skeleton="quick-action" className="h-7 w-14" />
        </div>
      </div>
      <div className={cn("flex shadow-[inset_0_-1px_0_var(--border)]", sheet ? "-mx-4 gap-4 px-4" : "gap-6")}>
        {TABS.map((width, index) => (
          <div key={index} data-skeleton="tab" className={cn("flex items-center border-b-2 border-transparent", sheet ? "py-2.5" : "py-3")}>
            <Skeleton className={cn("h-5", width)} />
          </div>
        ))}
      </div>
      <div className="grid gap-1 py-5">
        {Array.from({ length: PROPERTIES }, (_, index) => (
          <div key={index} data-skeleton="property" className="grid grid-cols-[10rem_minmax(0,1fr)] items-center gap-3">
            <div className="flex h-8 items-center gap-2">
              <Skeleton className="size-4 shrink-0" />
              <Skeleton className="h-3.5 w-20" />
            </div>
            <div className="flex h-8 items-center px-2.5">
              <Skeleton className={cn("h-3.5", widthAt(index))} />
            </div>
          </div>
        ))}
      </div>
    </LoadingStatus>
  );
}
