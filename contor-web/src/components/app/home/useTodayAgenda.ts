import { useState } from "react";
import { useListRecords } from "@/lib/api/generated/endpoints/records/records";
import type { RecordDto } from "@/lib/api/generated/model/recordDto";
import { TASK_LIST_PARAMS } from "@/components/app/tasks/task-model";
import { describeDue, type DueDescription } from "@/lib/due";
import { useSystemCollection } from "@/lib/hooks/useSystemCollection";

export interface DueTask {
  task: RecordDto;
  due: DueDescription;
}

function startOfDay(date: Date): Date {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate());
}

export function eventStart(event: RecordDto): Date | null {
  const value = event.values.starts_at;
  return typeof value === "string" ? new Date(value) : null;
}

/**
 * Today's events (calendar collection `events`) and the to-dos (`tasks`) that are still open, with those due today or
 * overdue. The to-do query shares its key with the to-do page, so both use one cache entry.
 */
export function useTodayAgenda() {
  const events = useSystemCollection("events");
  const tasks = useSystemCollection("tasks");
  // Fixed per mount so the query key stays stable.
  const [now] = useState(() => new Date());
  const today = startOfDay(now);
  const tomorrow = new Date(today.getFullYear(), today.getMonth(), today.getDate() + 1);

  const eventQuery = useListRecords(
    events.collection?.id ?? 0,
    { filter: [`starts_at:gte:${today.toISOString()}`, `starts_at:lt:${tomorrow.toISOString()}`], sort: "starts_at", size: 50 },
    { query: { enabled: events.collection !== undefined } },
  );
  const taskQuery = useListRecords(tasks.collection?.id ?? 0, TASK_LIST_PARAMS, { query: { enabled: tasks.collection !== undefined } });

  const openTasks = (taskQuery.data?.items ?? []).filter((task) => task.values.state !== "done");
  const dueTasks: DueTask[] = openTasks.flatMap((task) => {
    const due = describeDue(typeof task.values.due_date === "string" ? task.values.due_date : null, now);
    return due && (due.tone === "overdue" || due.tone === "today") ? [{ task, due }] : [];
  });

  return {
    now,
    events: eventQuery.data?.items,
    eventCount: eventQuery.data?.totalElements,
    openTasks: taskQuery.data ? openTasks : undefined,
    dueTasks,
    /** Collections still loading, or a collection's records. A missing collection counts as loaded (empty). */
    eventsLoading: events.isLoading || (events.collection !== undefined && eventQuery.isPending),
    tasksLoading: tasks.isLoading || (tasks.collection !== undefined && taskQuery.isPending),
    error: events.error ?? eventQuery.error ?? taskQuery.error,
  };
}
