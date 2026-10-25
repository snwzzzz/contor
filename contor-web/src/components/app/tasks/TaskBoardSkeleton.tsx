import { LoadingStatus } from "@/components/app/LoadingSkeletons";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";
import { TASK_COLUMNS } from "./task-model";

const SKELETON_CARDS = [3, 2, 2] as const;

/** `KanbanBoard`'s columns (boxed, named header, the same height) with a few placeholder `TaskCard`s. */
function BoardColumns() {
  return (
    <div className="grid gap-4 lg:grid-cols-3">
      {TASK_COLUMNS.map((column, columnIndex) => (
        <div key={column.key} data-skeleton="column" className="flex min-w-0 flex-col rounded-xl border bg-muted/30 lg:h-[calc(100svh-15rem)] lg:min-h-96">
          <div className="flex shrink-0 items-center gap-2 border-b px-3 py-2.5 text-sm font-medium">
            <column.icon aria-hidden="true" className={cn("size-4", column.iconClass)} />
            {column.title}
            <Skeleton className="ml-auto h-5 w-7 rounded-full" />
          </div>
          <div className="grid min-h-24 content-start gap-2 p-2">
            {Array.from({ length: SKELETON_CARDS[columnIndex] }, (_, index) => (
              <div key={index} data-skeleton="card" className="flex items-start gap-3 rounded-lg border bg-card p-3">
                <Skeleton className="mt-0.5 size-4 shrink-0 rounded-[4px]" />
                <div className="grid min-w-0 flex-1 gap-1">
                  <div className="flex h-5 items-center">
                    <Skeleton className={cn("h-3.5", index % 2 === 0 ? "w-3/4" : "w-1/2")} />
                  </div>
                  <Skeleton className="h-3 w-20" />
                </div>
              </div>
            ))}
          </div>
        </div>
      ))}
    </div>
  );
}

/** The board while the to-dos load (the field for a new to-do above it is already there). */
export function TaskBoardSkeleton() {
  return (
    <LoadingStatus label="Loading to-dos">
      <BoardColumns />
    </LoadingStatus>
  );
}

/** The whole to-do page below its header: the field for a new to-do and the board. */
export function TasksSkeleton() {
  return (
    <LoadingStatus label="Loading to-dos" className="grid gap-6">
      <Skeleton data-skeleton="new-task" className="h-8 w-full max-w-xl" />
      <BoardColumns />
    </LoadingStatus>
  );
}
