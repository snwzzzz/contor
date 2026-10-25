import { LoadingStatus, widthAt } from "@/components/app/LoadingSkeletons";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";
import { ReadingPanePlaceholder } from "./ReadingPanePlaceholder";

/** One `EmailRow` box: the 36px picture, sender and time, subject and one line of preview. */
function EmailBoxSkeleton({ index }: { index: number }) {
  return (
    <div data-skeleton="email" className="flex gap-3 rounded-lg border bg-surface-email px-3.5 py-3">
      <Skeleton className="mt-0.5 size-9 shrink-0 rounded-full" />
      <div className="grid min-w-0 flex-1 gap-0.5">
        <div className="flex h-5 items-center justify-between gap-2">
          <Skeleton className={cn("h-3.5", index % 2 === 0 ? "w-32" : "w-24")} />
          <Skeleton className="h-3 w-10 shrink-0" />
        </div>
        <div className="flex h-5 items-center">
          <Skeleton className={cn("h-3.5", widthAt(index))} />
        </div>
        <div className="flex h-4 items-center">
          <Skeleton className={cn("h-3", widthAt(index + 3))} />
        </div>
      </div>
    </div>
  );
}

/** `EmailList` as placeholders: a period heading ("Today") over the email boxes. */
function EmailBoxes({ rows }: { rows: number }) {
  return (
    <div className="grid gap-2">
      <div data-skeleton="period" className="flex h-6 items-center">
        <Skeleton className="h-3 w-14" />
      </div>
      {Array.from({ length: rows }, (_, index) => (
        <EmailBoxSkeleton key={index} index={index} />
      ))}
    </div>
  );
}

/** The message list while the emails load (inside the list's scroll area; the search and filter above are real). */
export function EmailListSkeleton({ rows = 8 }: { rows?: number }) {
  return (
    <LoadingStatus label="Loading emails">
      <EmailBoxes rows={rows} />
    </LoadingStatus>
  );
}

/**
 * The inbox while its route loads: the list column (Inbox | Sent, search and filter, email boxes) and the empty
 * reading pane, with `InboxView`'s widths, so nothing moves when the page arrives.
 */
export function InboxSkeleton() {
  return (
    <LoadingStatus label="Loading inbox" className="flex min-h-0 flex-1 flex-col gap-6 lg:flex-row">
      <div className="flex min-h-0 flex-col gap-3 max-lg:max-h-[55svh] lg:w-[24rem] lg:shrink-0 xl:w-[28rem]">
        <Skeleton data-skeleton="folder-switch" className="h-8 w-32 rounded-lg" />
        <div className="flex shrink-0 items-center gap-2">
          <Skeleton data-skeleton="search" className="h-9 flex-1" />
          <Skeleton className="h-9 w-20 shrink-0" />
        </div>
        <div className="min-h-0 flex-1 overflow-hidden pr-1">
          <EmailBoxes rows={8} />
        </div>
      </div>
      <div data-skeleton="reading-pane" className="flex min-h-0 min-w-0 flex-1 flex-col max-lg:min-h-[60svh]">
        <ReadingPanePlaceholder />
      </div>
    </LoadingStatus>
  );
}

/**
 * A conversation in the reading pane while it loads: the subject box and one box per message (40px picture, sender
 * and time, a line of preview). Without `subject` only the messages still coming (the first one is already shown).
 */
export function ThreadSkeleton({ subject = true, messages = 3 }: { subject?: boolean; messages?: number }) {
  return (
    <LoadingStatus label="Loading conversation" className="flex min-h-0 min-w-0 flex-1 flex-col gap-2">
      {subject ? (
        // The subject's line box (text-lg, leading-snug) inside the header's padding.
        <div data-skeleton="subject" className="shrink-0 rounded-lg border bg-surface-email px-5 py-3.5 shadow-xs">
          <div className="flex h-[1.547rem] items-center">
            <Skeleton className="h-5 w-2/3" />
          </div>
        </div>
      ) : null}
      {Array.from({ length: messages }, (_, index) => (
        <div key={index} data-skeleton="message" className="flex shrink-0 items-start gap-3 rounded-lg border bg-surface-email px-5 py-3.5 shadow-xs">
          <Skeleton className="size-10 shrink-0 rounded-full" />
          <div className="grid min-w-0 flex-1 gap-0.5">
            <div className="flex h-5 items-center justify-between gap-3">
              <Skeleton className={cn("h-3.5", index % 2 === 0 ? "w-28" : "w-36")} />
              <Skeleton className="h-3 w-24 shrink-0" />
            </div>
            <div className="flex h-4 items-center">
              <Skeleton className={cn("h-3", widthAt(index + 1))} />
            </div>
          </div>
        </div>
      ))}
    </LoadingStatus>
  );
}
