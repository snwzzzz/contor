"use client";

import { useQueryClient } from "@tanstack/react-query";
import { Check, CircleDashed, CircleDot, CircleCheck, ListTodo } from "lucide-react";
import { useState, type FormEvent } from "react";
import { KanbanBoard, type BoardColumn } from "@/components/app/board/KanbanBoard";
import { EmptyState } from "@/components/app/EmptyState";
import { ErrorMessage } from "@/components/app/ErrorMessage";
import { Input } from "@/components/ui/input";
import { getListRecordsQueryKey, useCreateRecord, useListRecords, useUpdateRecord } from "@/lib/api/generated/endpoints/records/records";
import type { RecordDto } from "@/lib/api/generated/model/recordDto";
import type { RecordPageDto } from "@/lib/api/generated/model/recordPageDto";
import { planPositions, sortForBoard } from "@/lib/board-order";
import { CARD_HOVER } from "@/lib/card-hover";
import { describeDue } from "@/lib/due";
import { useSystemCollection } from "@/lib/hooks/useSystemCollection";
import { cn } from "@/lib/utils";
import { TaskDetail } from "./TaskDetail";
import { DUE_TONE, PRIORITY, stateOf, stringValue, TASK_LIST_PARAMS, type State } from "./task-model";

const COLUMNS: readonly BoardColumn<State>[] = [
  { key: "open", title: "To do", icon: CircleDashed, iconClass: "text-muted-foreground", empty: "Nothing to do." },
  { key: "in_progress", title: "In progress", icon: CircleDot, iconClass: "text-tint-8-vivid", empty: "Nothing started." },
  { key: "done", title: "Done", icon: CircleCheck, iconClass: "text-tint-5-vivid", empty: "Nothing done yet." },
];

/** Fallback order of cards never moved by hand: earliest deadline first (overdue on top), none last, then by priority. */
function byUrgency(a: RecordDto, b: RecordDto): number {
  const dueA = stringValue(a, "due_date") ?? "9999-12-31";
  const dueB = stringValue(b, "due_date") ?? "9999-12-31";
  if (dueA !== dueB) return dueA < dueB ? -1 : 1;
  const rank = (task: RecordDto) => PRIORITY[stringValue(task, "priority") ?? "normal"]?.rank ?? 1;
  return rank(a) - rank(b);
}

/** The board's columns: each column's to-dos in display order (hand-made order, see `board-order`). */
function columnsOf(tasks: readonly RecordDto[]): Record<State, RecordDto[]> {
  const columns: Record<State, RecordDto[]> = { open: [], in_progress: [], done: [] };
  for (const task of tasks) columns[stateOf(task)].push(task);
  for (const state of Object.keys(columns) as State[]) columns[state] = sortForBoard(columns[state], byUrgency);
  return columns;
}

/**
 * The assistant's to-do list (system collection `tasks`) as a board: To do, In progress and Done side by side. Cards
 * are dragged within and between columns (the order is kept per to-do in `extra.boardPosition`); the checkbox moves a
 * card to the top of another column, a click opens the detail sidebar (editable).
 */
export function TaskList() {
  const queryClient = useQueryClient();
  const { collection, isLoading, error } = useSystemCollection("tasks");
  const collectionId = collection?.id ?? 0;
  const { data } = useListRecords(collectionId, TASK_LIST_PARAMS, { query: { enabled: collection !== undefined } });
  const [title, setTitle] = useState("");
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const refresh = () => queryClient.invalidateQueries({ queryKey: getListRecordsQueryKey(collectionId) });
  const update = useUpdateRecord();
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

  /** Puts a to-do into `state` at `index` (counted without the to-do): new state and the positions that change. */
  async function move(task: RecordDto, state: State, index: number) {
    const moved = { ...task, values: { ...task.values, state } };
    const order = columnsOf(data?.items ?? [])[state].filter((item) => item.id !== task.id);
    order.splice(index, 0, moved);
    const positions = planPositions(order, task.id);
    const stateChanged = stateOf(task) !== state;

    // Optimistic: the card lands in its new place right away; the refresh afterwards shows what the server has.
    queryClient.setQueryData<RecordPageDto>(getListRecordsQueryKey(collectionId, TASK_LIST_PARAMS), (page) => page && {
      ...page,
      items: page.items.map((item) => {
        const position = positions.get(item.id);
        const values = item.id === task.id ? moved.values : item.values;
        return position === undefined ? { ...item, values } : { ...item, values, extra: { ...item.extra, boardPosition: position } };
      }),
    });
    const requests = order.flatMap((item) => {
      const position = positions.get(item.id);
      const newState = item.id === task.id && stateChanged;
      if (position === undefined && !newState) return [];
      return [update.mutateAsync({
        collectionId,
        recordId: item.id,
        data: {
          ...(newState ? { values: { state } } : {}),
          ...(position !== undefined ? { extra: { ...item.extra, boardPosition: position } } : {}),
        },
      })];
    });
    try {
      await Promise.all(requests);
    } catch {
      // Reported by the global mutation error toast; the refresh puts the cards back where the server has them.
    } finally {
      await refresh();
    }
  }

  const columns = columnsOf(data?.items ?? []);
  const today = new Date();
  const selected = data?.items.find((item) => item.id === selectedId);

  return (
    <div className="grid gap-6">
      <form onSubmit={add} className="max-w-xl">
        <Input
          aria-label="New to-do"
          placeholder="Add a to-do and press Enter"
          value={title}
          disabled={create.isPending}
          onChange={(event) => setTitle(event.target.value)}
        />
      </form>

      <KanbanBoard
        columns={COLUMNS}
        items={columns}
        getId={(task) => task.id}
        renderCard={(task) => (
          <TaskCard task={task} today={today} onOpen={() => setSelectedId(task.id)} onMove={(state) => void move(task, state, 0)} />
        )}
        renderPreview={(task, state) => <TaskPreview task={{ ...task, values: { ...task.values, state } }} today={today} />}
        onMove={(task, state, index) => void move(task, state, index)}
      />

      {selected ? (
        <TaskDetail
          key={selected.id}
          collection={collection}
          task={selected}
          today={today}
          onMove={(state) => void move(selected, state, 0)}
          onChanged={() => void refresh()}
          onClose={() => setSelectedId(null)}
        />
      ) : null}
    </div>
  );
}

/** The whole card as it will look in the column it is dragged over (checked and struck through in Done). */
function TaskPreview({ task, today }: { task: RecordDto; today: Date }) {
  const done = stateOf(task) === "done";
  return (
    <div className="flex items-start gap-3 p-3">
      <span
            className={cn(
              "mt-0.5 flex size-4 shrink-0 items-center justify-center rounded-[4px] border",
              done ? "border-primary bg-primary text-primary-foreground" : "border-input",
            )}
          >
        {done ? <Check className="size-3" strokeWidth={3} /> : null}
      </span>
      <span className="grid min-w-0 flex-1 gap-1">
        <TaskSummary task={task} today={today} />
      </span>
    </div>
  );
}

/** The face of a to-do on the board: title, notes, deadline relative to today and priority. */
function TaskSummary({ task, today }: { task: RecordDto; today: Date }) {
  const done = stateOf(task) === "done";
  const title = task.title ?? `To-do #${task.id}`;
  const due = describeDue(stringValue(task, "due_date"), today);
  const priority = PRIORITY[stringValue(task, "priority") ?? ""];
  const notes = stringValue(task, "notes");
  return (
    <>
      <span className={cn("text-sm font-medium", done && "text-muted-foreground line-through")}>{title}</span>
      {notes ? <span className="line-clamp-2 text-xs text-muted-foreground">{notes}</span> : null}
      {due || (priority && priority.label !== "Normal") ? (
        <span className="flex flex-wrap items-center gap-2 text-xs">
          {due ? <span className={cn("font-medium", done ? "text-muted-foreground" : DUE_TONE[due.tone])}>{due.label}</span> : null}
          {priority && priority.label !== "Normal" ? <span className={cn("rounded-full px-2 py-0.5 font-medium", priority.className)}>{priority.label}</span> : null}
        </span>
      ) : null}
    </>
  );
}

interface TaskCardProps {
  task: RecordDto;
  today: Date;
  onOpen: () => void;
  onMove: (state: State) => void;
}

/** One to-do: checkbox, and title, deadline, priority and notes as one button that opens the detail sidebar. */
function TaskCard({ task, today, onOpen, onMove }: TaskCardProps) {
  const done = stateOf(task) === "done";
  const title = task.title ?? `To-do #${task.id}`;

  return (
    <div className={cn("rounded-lg border bg-card", CARD_HOVER)}>
      <div className="flex items-start gap-3 p-3">
        <input
          type="checkbox"
          className="mt-0.5 size-4 shrink-0 accent-primary"
          aria-label={title}
          checked={done}
          onChange={(event) => onMove(event.target.checked ? "done" : "open")}
        />
        <button type="button" aria-haspopup="dialog" aria-label={`Open ${title}`} onClick={onOpen} className="grid min-w-0 flex-1 cursor-pointer gap-1 text-left">
          <TaskSummary task={task} today={today} />
        </button>
      </div>
    </div>
  );
}
