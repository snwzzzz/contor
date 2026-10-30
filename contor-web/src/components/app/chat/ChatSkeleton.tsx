import { LoadingStatus, widthAt } from "@/components/app/LoadingSkeletons";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";

const ROWS = 6;
const LINES = 3;
const SOURCES = 3;

/** The conversation list's rows until they are there (inside the side list, which keeps its header). */
export function ChatListSkeleton() {
  return (
    <div aria-hidden="true" className="grid gap-px">
      {Array.from({ length: ROWS }, (_, row) => (
        <div key={row} data-skeleton="conversation" className="flex h-8 items-center gap-2 px-2">
          <Skeleton className={cn("h-3.5", widthAt(row))} />
        </div>
      ))}
    </div>
  );
}

/** One conversation until it is there, laid out like `ChatThread`: title row, question, answer, sources column. */
export function ChatThreadSkeleton() {
  return (
    <LoadingStatus label="Loading conversation" className="grid min-w-0 gap-6">
      <div className="flex h-9 items-center border-b pb-3">
        <Skeleton className="h-5 w-64 max-w-full" />
      </div>
      <div className="grid gap-8 lg:grid-cols-[minmax(0,1fr)_20rem]">
        <div className="grid min-w-0 content-start gap-6">
          <div data-skeleton="question" className="flex justify-end">
            <Skeleton className="h-10 w-1/2 max-w-[85%] rounded-2xl rounded-tr-sm" />
          </div>
          <div data-skeleton="answer" className="grid gap-2">
            {Array.from({ length: LINES }, (_, line) => (
              <Skeleton key={line} className={cn("h-4", line === LINES - 1 ? widthAt(line) : "w-full")} />
            ))}
          </div>
        </div>
        <div className="grid min-w-0 content-start gap-2">
          <Skeleton className="h-4 w-16" />
          {Array.from({ length: SOURCES }, (_, index) => (
            <div key={index} data-skeleton="source" className="flex items-start gap-2 rounded-lg border px-3 py-2">
              <Skeleton className="size-5 shrink-0" />
              <Skeleton className={cn("h-3.5", widthAt(index))} />
            </div>
          ))}
        </div>
      </div>
    </LoadingStatus>
  );
}

/** The whole chat page body while its route loads: the side list and a conversation (or the start page). */
export function ChatSkeleton({ conversation = false }: { conversation?: boolean }) {
  return (
    <div className="grid gap-6 lg:min-h-0 lg:flex-1 lg:grid-cols-[16rem_minmax(0,1fr)] lg:gap-0">
      <div className="max-lg:hidden lg:border-r lg:pt-4 lg:pr-4">
        <div className="mb-2 flex h-8 items-center">
          <Skeleton className="h-4 w-24" />
        </div>
        <ChatListSkeleton />
      </div>
      <div className="min-w-0 lg:pt-4 lg:pl-6">
        {conversation ? (
          <ChatThreadSkeleton />
        ) : (
          <LoadingStatus label="Loading chat" className="mx-auto grid w-full max-w-3xl gap-3 pt-[15vh]">
            <Skeleton className="mx-auto h-6 w-72 max-w-full" />
            <Skeleton className="h-16 w-full rounded-md" />
          </LoadingStatus>
        )}
      </div>
    </div>
  );
}
