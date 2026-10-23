"use client";

import { CalendarDays } from "lucide-react";
import Link from "next/link";
import { DUE_TONE } from "@/components/app/tasks/task-model";
import { ErrorMessage } from "@/components/app/ErrorMessage";
import { Skeleton } from "@/components/ui/skeleton";
import type { RecordDto } from "@/lib/api/generated/model/recordDto";
import { ROW_HOVER } from "@/lib/card-hover";
import { formatTime, useTimeFormat } from "@/lib/time-format";
import { cn } from "@/lib/utils";
import { HomeSection, QuietEmpty, SectionLink } from "./HomeSection";
import { Pager, usePages } from "./Pager";
import { eventStart, useTodayAgenda, type DueTask } from "./useTodayAgenda";

const PER_PAGE = 4;

/** Events first (by time), then the due to-dos. */
type AgendaRow = { kind: "event"; event: RecordDto } | { kind: "task"; entry: DueTask };

const ROW = cn("flex items-baseline gap-3 rounded-md px-2 py-1.5 outline-none focus-visible:ring-2 focus-visible:ring-ring", ROW_HOVER);

/** Today's calendar entries and the to-dos that are due today or overdue. */
export function TodayCard() {
  const { events, dueTasks, eventsLoading, tasksLoading, error } = useTodayAgenda();
  const rows: AgendaRow[] = [
    ...(events ?? []).map((event) => ({ kind: "event" as const, event })),
    ...dueTasks.map((entry) => ({ kind: "task" as const, entry })),
  ];
  const pages = usePages(rows, PER_PAGE);

  return (
    <HomeSection
      title="Today"
      action={
        <>
          <SectionLink href="/calendar">Calendar</SectionLink>
          <SectionLink href="/tasks">To-dos</SectionLink>
        </>
      }
    >
      {error ? (
        <ErrorMessage message={error.message} />
      ) : eventsLoading || tasksLoading ? (
        <div aria-label="Loading today" className="grid gap-2 py-1">
          <Skeleton className="h-5" />
          <Skeleton className="h-5 w-4/5" />
        </div>
      ) : rows.length === 0 ? (
        <QuietEmpty icon={CalendarDays} title="Nothing on today" description="No events and no to-dos due." />
      ) : (
        <>
          <ul className="-mx-2">
            {pages.items.map((row) =>
              row.kind === "event" ? (
                <li key={`event-${row.event.id}`}>
                  <EventRow event={row.event} />
                </li>
              ) : (
                <li key={`task-${row.entry.task.id}`}>
                  <TaskRow entry={row.entry} />
                </li>
              ),
            )}
          </ul>
          <Pager label="Today" {...pages} />
        </>
      )}
    </HomeSection>
  );
}

function EventRow({ event }: { event: RecordDto }) {
  const format = useTimeFormat();
  const start = eventStart(event);
  return (
    <Link href="/calendar" className={ROW}>
      <span className="w-16 shrink-0 text-xs text-muted-foreground tabular-nums">
        {event.values.all_day === true || !start ? "All day" : formatTime(start, format)}
      </span>
      <span className="min-w-0 truncate text-sm">{event.title || "(Untitled event)"}</span>
    </Link>
  );
}

function TaskRow({ entry }: { entry: DueTask }) {
  return (
    <Link href="/tasks" className={ROW}>
      <span className="w-16 shrink-0 text-xs text-muted-foreground">To-do</span>
      <span className="min-w-0 flex-1 truncate text-sm">{entry.task.title || "(Untitled to-do)"}</span>
      <span className={cn("shrink-0 text-xs", entry.due.tone === "overdue" ? DUE_TONE.overdue : "text-muted-foreground")}>{entry.due.label}</span>
    </Link>
  );
}
