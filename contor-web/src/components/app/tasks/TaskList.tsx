"use client";

import { useQueryClient } from "@tanstack/react-query";
import { ArrowUpRight, Bot, Check, ChevronDown, CircleDashed, CircleDot, CircleCheck, ListTodo, User } from "lucide-react";
import Link from "next/link";
import { useId, useState, type FormEvent } from "react";
import { AppButton } from "@/components/app/AppButton";
import { KanbanBoard, type BoardColumn } from "@/components/app/board/KanbanBoard";
import { recordHref } from "@/components/app/collections/CollectionTable";
import { EmptyState } from "@/components/app/EmptyState";
import { ErrorMessage } from "@/components/app/ErrorMessage";
import { Input } from "@/components/ui/input";
import { getListRecordsQueryKey, useCreateRecord, useListRecords, useUpdateRecord } from "@/lib/api/generated/endpoints/records/records";
import type { RecordDto } from "@/lib/api/generated/model/recordDto";
import type { RecordPageDto } from "@/lib/api/generated/model/recordPageDto";
import { planPositions, sortForBoard } from "@/lib/board-order";
import { describeDue, type DueTone } from "@/lib/due";
import { useSystemCollection } from "@/lib/hooks/useSystemCollection";
import { cn } from "@/lib/utils";

const PARAMS = { sort: "due_date", size: 200 } as const;
const ADDED_FORMAT = new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "short", year: "numeric" });

type State = "open" | "in_progress" | "done";

const COLUMNS: readonly BoardColumn<State>[] = [
  { key: "open", title: "To do", icon: CircleDashed, iconClass: "text-muted-foreground", empty: "Nothing to do." },
  { key: "in_progress", title: "In progress", icon: CircleDot, iconClass: "text-tint-8-vivid", empty: "Nothing started." },
  { key: "done", title: "Done", icon: CircleCheck, iconClass: "text-tint-5-vivid", empty: "Nothing done yet." },
];

const DUE_TONE: Record<DueTone, string> = {
  overdue: "text-destructive",
  today: "text-tint-2-vivid",
  soon: "text-foreground",
  later: "text-muted-foreground",
};

const PRIORITY: Record<string, { label: string; className: string; rank: number }> = {
  high: { label: "High", className: "bg-destructive/15 text-destructive", rank: 0 },
  normal: { label: "Normal", className: "bg-muted text-muted-foreground", rank: 1 },
  low: { label: "Low", className: "bg-info/15 text-info", rank: 2 },
};

function stateOf(task: RecordDto): State {
  const state = task.values.state;
  return state === "in_progress" || state === "done" ? state : "open";
}

function stringValue(task: RecordDto, key: string): string | null {
  const value = task.values[key];
  return typeof value === "string" && value ? value : null;
}

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
 * are dragged within and between columns (the order is kept per to-do in `extra.boardPosition`); the checkbox and the
 * buttons in a card move it to the top of another column.
 */
export function TaskList() {
  const queryClient = useQueryClient();
  const { collection, isLoading, error } = useSystemCollection("tasks");
  const collectionId = collection?.id ?? 0;
  const { data } = useListRecords(collectionId, PARAMS, { query: { enabled: collection !== undefined } });
  const [title, setTitle] = useState("");
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
    queryClient.setQueryData<RecordPageDto>(getListRecordsQueryKey(collectionId, PARAMS), (page) => page && {
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
        renderCard={(task) => <TaskCard task={task} collectionId={collectionId} today={today} onMove={(state) => void move(task, state, 0)} />}
        renderPreview={(task, state) => <TaskPreview task={{ ...task, values: { ...task.values, state } }} today={today} />}
        onMove={(task, state, index) => void move(task, state, index)}
      />
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
        <TaskSummary task={task} today={today} expanded={false} />
      </span>
    </div>
  );
}

/** The collapsed face of a to-do: title, notes, deadline relative to today and priority. */
function TaskSummary({ task, today, expanded }: { task: RecordDto; today: Date; expanded: boolean }) {
  const done = stateOf(task) === "done";
  const title = task.title ?? `To-do #${task.id}`;
  const due = describeDue(stringValue(task, "due_date"), today);
  const priority = PRIORITY[stringValue(task, "priority") ?? ""];
  const notes = stringValue(task, "notes");
  return (
    <>
      <span className="flex items-start justify-between gap-2">
        <span className={cn("text-sm font-medium", done && "text-muted-foreground line-through")}>{title}</span>
        <ChevronDown className={cn("mt-0.5 size-4 shrink-0 text-muted-foreground transition-transform", expanded && "rotate-180")} aria-hidden="true" />
      </span>
      {notes && !expanded ? <span className="line-clamp-2 text-xs text-muted-foreground">{notes}</span> : null}
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
  collectionId: number;
  today: Date;
  onMove: (state: State) => void;
}

/** One to-do: checkbox, title, deadline, priority and notes; expands to all details and actions. */
function TaskCard({ task, collectionId, today, onMove }: TaskCardProps) {
  const [expanded, setExpanded] = useState(false);
  const detailsId = useId();
  const state = stateOf(task);
  const done = state === "done";
  const title = task.title ?? `To-do #${task.id}`;
  const due = describeDue(stringValue(task, "due_date"), today);
  const priority = PRIORITY[stringValue(task, "priority") ?? ""];
  const notes = stringValue(task, "notes");

  return (
    <div className={cn("rounded-lg border bg-card transition-shadow hover:shadow-sm", expanded && "shadow-sm")}>
      <div className="flex items-start gap-3 p-3">
        <input
          type="checkbox"
          className="mt-0.5 size-4 shrink-0 accent-primary"
          aria-label={title}
          checked={done}
          onChange={(event) => onMove(event.target.checked ? "done" : "open")}
        />
        <button
          type="button"
          aria-expanded={expanded}
          aria-controls={detailsId}
          onClick={() => setExpanded((open) => !open)}
          className="grid min-w-0 flex-1 gap-1 text-left"
        >
          <TaskSummary task={task} today={today} expanded={expanded} />
        </button>
      </div>

      {expanded ? (
        <div id={detailsId} role="region" aria-label={title} className="grid gap-3 border-t px-3 py-3 pl-10 text-sm animate-in fade-in duration-200">
          {notes ? <p className="whitespace-pre-line text-muted-foreground">{notes}</p> : <p className="text-muted-foreground">No notes.</p>}
          <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 text-xs">
            <dt className="text-muted-foreground">Deadline</dt>
            <dd>{stringValue(task, "due_date") ? `${due?.label} (${stringValue(task, "due_date")})` : "None"}</dd>
            <dt className="text-muted-foreground">Priority</dt>
            <dd>{priority?.label ?? "Normal"}</dd>
            <dt className="text-muted-foreground">Origin</dt>
            <dd className="flex items-center gap-1">
              {task.createdBy === "AI" ? <Bot className="size-3" aria-hidden="true" /> : <User className="size-3" aria-hidden="true" />}
              {task.createdBy === "AI" ? "Added by the assistant" : "Added by you"} on {ADDED_FORMAT.format(new Date(task.createdAt))}
            </dd>
          </dl>
          <div className="flex flex-wrap gap-2">
            {state !== "in_progress" && !done ? (
              <AppButton size="sm" variant="outline" onClick={() => onMove("in_progress")}>Start</AppButton>
            ) : null}
            {state === "in_progress" ? (
              <AppButton size="sm" variant="outline" onClick={() => onMove("open")}>Back to to do</AppButton>
            ) : null}
            {!done ? (
              <AppButton size="sm" onClick={() => onMove("done")}>Mark done</AppButton>
            ) : (
              <AppButton size="sm" variant="outline" onClick={() => onMove("open")}>Reopen</AppButton>
            )}
            <Link href={recordHref(collectionId, task.id)} className="ml-auto inline-flex items-center gap-1 text-xs text-muted-foreground hover:text-foreground">
              Open details
              <ArrowUpRight className="size-3" aria-hidden="true" />
            </Link>
          </div>
        </div>
      ) : null}
    </div>
  );
}
