"use client";

import { Bot, CircleCheck, CircleDashed, CircleDot, User } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { toast } from "sonner";
import { AppButton } from "@/components/app/AppButton";
import { recordHref } from "@/components/app/collections/CollectionTable";
import { RecordFieldInput } from "@/components/app/collections/RecordFieldInput";
import { DetailSheet } from "@/components/app/DetailSheet";
import { Input } from "@/components/ui/input";
import { useDeleteRecord, useListRecordEmails, useUpdateRecord } from "@/lib/api/generated/endpoints/records/records";
import type { CollectionDto } from "@/lib/api/generated/model/collectionDto";
import type { RecordDto } from "@/lib/api/generated/model/recordDto";
import { describeDue } from "@/lib/due";
import { cn } from "@/lib/utils";
import { DUE_TONE, stateOf, type State } from "./task-model";

const ADDED_FORMAT = new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "short", year: "numeric" });
const STATE_ICON = { open: CircleDashed, in_progress: CircleDot, done: CircleCheck } as const;
const STATE_LABEL: Record<State, string> = { open: "To do", in_progress: "In progress", done: "Done" };
const SOURCE_EMAILS = { size: 5 } as const;

interface TaskDetailProps {
  collection: CollectionDto;
  task: RecordDto;
  today: Date;
  /** Puts the to-do on top of another column (the board's own move). */
  onMove: (state: State) => void;
  /** The to-do was saved or deleted: refresh the board. */
  onChanged: () => void;
  onClose: () => void;
}

function sameValue(a: unknown, b: unknown): boolean {
  return (a ?? null) === (b ?? null);
}

/**
 * One to-do in the detail sidebar: title and fields (from the collection, so new fields show up without code) are
 * editable with an explicit Save that sends only what changed; quick actions move it between the columns.
 */
export function TaskDetail({ collection, task, today, onMove, onChanged, onClose }: TaskDetailProps) {
  const fields = collection.fields
    .filter((field) => !field.archivedAt && field.slug !== "state")
    .toSorted((a, b) => a.position - b.position);
  const [title, setTitle] = useState(task.title ?? "");
  const [values, setValues] = useState<Record<string, unknown>>(
    () => Object.fromEntries(fields.map((field) => [field.slug, task.values[field.slug] ?? null])),
  );
  const [confirming, setConfirming] = useState<"discard" | "delete" | null>(null);
  const update = useUpdateRecord({ mutation: { meta: { silent: true } } });
  const remove = useDeleteRecord({ mutation: { meta: { silent: true } } });
  const { data: emails } = useListRecordEmails(collection.id, task.id, SOURCE_EMAILS, { query: { retry: false } });

  const changedValues = Object.fromEntries(
    fields.filter((field) => !sameValue(values[field.slug], task.values[field.slug])).map((field) => [field.slug, values[field.slug]]),
  );
  const titleChanged = title.trim() !== (task.title ?? "");
  const dirty = titleChanged || Object.keys(changedValues).length > 0;
  const state = stateOf(task);
  const heading = task.title ?? `To-do #${task.id}`;
  const due = describeDue(typeof values.due_date === "string" ? values.due_date : null, today);
  const error = update.error?.message ?? remove.error?.message;

  function requestClose() {
    if (dirty) setConfirming("discard");
    else onClose();
  }

  function save() {
    update.mutate(
      {
        collectionId: collection.id,
        recordId: task.id,
        data: {
          ...(titleChanged ? { title: title.trim() } : {}),
          ...(Object.keys(changedValues).length > 0 ? { values: changedValues } : {}),
        },
      },
      {
        onSuccess: () => {
          toast.success("Saved");
          onChanged();
        },
      },
    );
  }

  function destroy() {
    remove.mutate(
      { collectionId: collection.id, recordId: task.id },
      {
        onSuccess: () => {
          onChanged();
          onClose();
        },
      },
    );
  }

  return (
    <DetailSheet
      open
      onOpenChange={(open) => !open && requestClose()}
      title={heading}
      label="To-do"
      icon={STATE_ICON[state]}
      tint={5}
      aside={<span className="shrink-0 rounded-full bg-muted px-2 py-0.5 text-xs text-muted-foreground">{STATE_LABEL[state]}</span>}
      footer={
        confirming === "delete" ? (
          <>
            <AppButton variant="ghost" disabled={remove.isPending} onClick={() => setConfirming(null)}>Cancel</AppButton>
            <AppButton variant="destructive" loading={remove.isPending} onClick={destroy}>Delete to-do</AppButton>
          </>
        ) : (
          <>
            <AppButton variant="ghost" className="mr-auto text-destructive" onClick={() => setConfirming("delete")}>Delete</AppButton>
            <AppButton variant="ghost" onClick={requestClose}>Cancel</AppButton>
            <AppButton disabled={!dirty} loading={update.isPending} onClick={save}>Save</AppButton>
          </>
        )
      }
    >
      <div className="grid gap-5">
        {confirming === "discard" ? (
          <div
            role="alertdialog"
            aria-label="Unsaved changes"
            className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-warning/50 bg-warning/10 px-3 py-2 text-sm"
          >
            <span>Discard your changes?</span>
            <span className="flex gap-2">
              <AppButton size="sm" variant="ghost" onClick={() => setConfirming(null)}>Keep editing</AppButton>
              <AppButton size="sm" variant="outline" onClick={onClose}>Discard</AppButton>
            </span>
          </div>
        ) : null}

        <Input aria-label="Title" value={title} onChange={(event) => setTitle(event.target.value)} className="font-medium" />

        <div className="flex flex-wrap gap-2">
          {state === "open" ? <AppButton size="sm" variant="outline" onClick={() => onMove("in_progress")}>Start</AppButton> : null}
          {state === "in_progress" ? <AppButton size="sm" variant="outline" onClick={() => onMove("open")}>Back to to do</AppButton> : null}
          {state === "done" ? (
            <AppButton size="sm" variant="outline" onClick={() => onMove("open")}>Reopen</AppButton>
          ) : (
            <AppButton size="sm" onClick={() => onMove("done")}>Mark done</AppButton>
          )}
        </div>

        <div className="grid gap-4">
          {fields.map((field) => (
            <div key={field.slug} className="grid gap-1">
              <RecordFieldInput
                field={field}
                value={values[field.slug]}
                onChange={(next) => setValues((current) => ({ ...current, [field.slug]: next }))}
              />
              {field.slug === "due_date" && due ? (
                <span className={cn("text-xs font-medium", state === "done" ? "text-muted-foreground" : DUE_TONE[due.tone])}>{due.label}</span>
              ) : null}
            </div>
          ))}
        </div>

        {error ? <p role="alert" className="text-sm text-destructive">{error}</p> : null}

        <section className="grid gap-2 text-sm">
          <h3 className="text-xs font-medium tracking-wide text-muted-foreground uppercase">Origin</h3>
          <p className="flex items-center gap-1.5 text-muted-foreground">
            {task.createdBy === "AI" ? <Bot className="size-3.5" aria-hidden="true" /> : <User className="size-3.5" aria-hidden="true" />}
            {task.createdBy === "AI" ? "Added by the assistant" : "Added by you"} on {ADDED_FORMAT.format(new Date(task.createdAt))}
          </p>
          {emails && emails.items.length > 0 ? (
            <ul className="grid gap-1">
              {emails.items.map((email) => (
                <li key={email.id}>
                  <Link href={`/inbox?email=${email.id}`} className="grid rounded-lg border bg-muted/30 px-3 py-2 hover:bg-muted/60">
                    <span className="truncate font-medium">{email.subject || "(No subject)"}</span>
                    <span className="truncate text-xs text-muted-foreground">{email.fromName || email.fromAddress}</span>
                  </Link>
                </li>
              ))}
            </ul>
          ) : null}
          <Link href={recordHref(collection.id, task.id)} className="w-fit text-xs text-muted-foreground hover:text-foreground">
            Open as page
          </Link>
        </section>
      </div>
    </DetailSheet>
  );
}
