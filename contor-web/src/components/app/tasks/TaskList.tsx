"use client";

import { useQueryClient } from "@tanstack/react-query";
import { ListTodo } from "lucide-react";
import { useState, type FormEvent } from "react";
import { EmptyState } from "@/components/app/EmptyState";
import { ErrorMessage } from "@/components/app/ErrorMessage";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import { getListRecordsQueryKey, useCreateRecord, useListRecords, useUpdateRecord } from "@/lib/api/generated/endpoints/records/records";
import type { RecordDto } from "@/lib/api/generated/model/recordDto";
import { useSystemCollection } from "@/lib/hooks/useSystemCollection";
import { cn } from "@/lib/utils";

const PARAMS = { sort: "due_date", size: 200 } as const;
const DUE_FORMAT = new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "short" });
const PRIORITY_LABEL: Record<string, string> = { high: "High", low: "Low" };

function isDone(task: RecordDto): boolean {
  return task.values.state === "done";
}

/** The assistant's to-do list (system collection `tasks`): open to-dos first, check off, add new ones. */
export function TaskList() {
  const queryClient = useQueryClient();
  const { collection, isLoading, error } = useSystemCollection("tasks");
  const collectionId = collection?.id ?? 0;
  const { data } = useListRecords(collectionId, PARAMS, { query: { enabled: collection !== undefined } });
  const [title, setTitle] = useState("");
  const refresh = () => queryClient.invalidateQueries({ queryKey: getListRecordsQueryKey(collectionId) });
  const update = useUpdateRecord({ mutation: { onSuccess: refresh } });
  const create = useCreateRecord({
    mutation: {
      onSuccess: () => {
        setTitle("");
        return refresh();
      },
    },
  });

  if (isLoading) {
    return <p className="text-sm text-muted-foreground">Loading to-dos…</p>;
  }
  if (error) {
    return <ErrorMessage message={error.message} />;
  }
  if (!collection) {
    return <EmptyState icon={ListTodo} title="No to-do list yet" description="The to-do collection is created when the backend starts." />;
  }

  function add(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const trimmed = title.trim();
    if (!trimmed) return;
    create.mutate({ collectionId, data: { title: trimmed, values: { state: "open" } } });
  }

  function toggle(task: RecordDto, done: boolean) {
    update.mutate({ collectionId, recordId: task.id, data: { values: { state: done ? "done" : "open" } } });
  }

  const tasks = data?.items ?? [];
  const open = tasks.filter((task) => !isDone(task));
  const done = tasks.filter(isDone);
  const today = new Date().toISOString().slice(0, 10);

  return (
    <div className="grid max-w-2xl gap-6">
      <form onSubmit={add}>
        <Input
          aria-label="New to-do"
          placeholder="Add a to-do and press Enter"
          value={title}
          disabled={create.isPending}
          onChange={(event) => setTitle(event.target.value)}
        />
      </form>

      {open.length === 0 ? (
        <p className="text-sm text-muted-foreground">Nothing open. The assistant adds to-dos it finds in your mail.</p>
      ) : (
        <ul aria-label="Open to-dos" className="grid gap-1">
          {open.map((task) => (
            <TaskItem key={task.id} task={task} today={today} onToggle={toggle} />
          ))}
        </ul>
      )}

      {done.length > 0 ? (
        <section className="grid gap-2">
          <h2 className="text-sm font-medium text-muted-foreground">Done ({done.length})</h2>
          <ul aria-label="Done" className="grid gap-1">
            {done.map((task) => (
              <TaskItem key={task.id} task={task} today={today} onToggle={toggle} />
            ))}
          </ul>
        </section>
      ) : null}
    </div>
  );
}

interface TaskItemProps {
  task: RecordDto;
  today: string;
  onToggle: (task: RecordDto, done: boolean) => void;
}

function TaskItem({ task, today, onToggle }: TaskItemProps) {
  const done = isDone(task);
  const due = typeof task.values.due_date === "string" ? task.values.due_date : null;
  const priority = typeof task.values.priority === "string" ? PRIORITY_LABEL[task.values.priority] : undefined;
  const overdue = !done && due !== null && due < today;
  return (
    <li className="flex items-center gap-3 rounded-md px-2 py-1.5 hover:bg-accent">
      <input
        type="checkbox"
        className="size-4 accent-primary"
        aria-label={task.title ?? `To-do #${task.id}`}
        checked={done}
        onChange={(event) => onToggle(task, event.target.checked)}
      />
      <span className={cn("flex-1 text-sm", done && "text-muted-foreground line-through")}>{task.title}</span>
      {priority ? <Badge variant={priority === "High" ? "destructive" : "outline"}>{priority}</Badge> : null}
      {due ? (
        <span className={cn("text-xs tabular-nums", overdue ? "text-destructive" : "text-muted-foreground")}>
          Due {DUE_FORMAT.format(new Date(`${due}T00:00:00`))}
        </span>
      ) : null}
    </li>
  );
}
