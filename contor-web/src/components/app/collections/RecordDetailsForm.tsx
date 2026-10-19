"use client";

import { useQueryClient } from "@tanstack/react-query";
import { useId, useState, type FormEvent } from "react";
import { toast } from "sonner";
import { AppButton } from "@/components/app/AppButton";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { getGetRecordQueryKey, getListRecordsQueryKey, useUpdateRecord } from "@/lib/api/generated/endpoints/records/records";
import type { CollectionFieldDto } from "@/lib/api/generated/model/collectionFieldDto";
import type { RecordDto } from "@/lib/api/generated/model/recordDto";
import { cn } from "@/lib/utils";
import { RecordFieldInput } from "./RecordFieldInput";

/** Values are compared as JSON, so money objects with equal content count as unchanged. */
function same(a: unknown, b: unknown): boolean {
  return JSON.stringify(a ?? null) === JSON.stringify(b ?? null);
}

function initialValues(fields: readonly CollectionFieldDto[], record: RecordDto): Record<string, unknown> {
  return Object.fromEntries(fields.map((field) => [field.slug, record.values[field.slug] ?? null]));
}

interface RecordDetailsFormProps {
  fields: readonly CollectionFieldDto[];
  record: RecordDto;
}

/**
 * The record's Details tab: the title and every field as an input (by field type). Save appears with the number of
 * changes once something is edited and sends only what changed; Discard goes back to the saved values.
 */
export function RecordDetailsForm({ fields, record }: RecordDetailsFormProps) {
  const queryClient = useQueryClient();
  const titleId = useId();
  const [title, setTitle] = useState(record.title ?? "");
  const [values, setValues] = useState(() => initialValues(fields, record));
  // The error is shown inline (meta.silent: no toast).
  const update = useUpdateRecord({ mutation: { meta: { silent: true } } });

  const changedValues = Object.fromEntries(
    fields.filter((field) => !same(values[field.slug], record.values[field.slug])).map((field) => [field.slug, values[field.slug]]),
  );
  const titleChanged = title.trim() !== (record.title ?? "");
  const changes = Object.keys(changedValues).length + (titleChanged ? 1 : 0);

  function discard() {
    setTitle(record.title ?? "");
    setValues(initialValues(fields, record));
    update.reset();
  }

  function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (changes === 0) return;
    update.mutate(
      {
        collectionId: record.collectionId,
        recordId: record.id,
        data: {
          ...(titleChanged ? { title: title.trim() } : {}),
          ...(Object.keys(changedValues).length > 0 ? { values: changedValues } : {}),
        },
      },
      {
        onSuccess: (saved) => {
          // The page shows the saved record; the form compares against it, so it is clean again.
          queryClient.setQueryData(getGetRecordQueryKey(record.collectionId, record.id), saved);
          void queryClient.invalidateQueries({ queryKey: getListRecordsQueryKey(record.collectionId) });
          toast.success("Saved");
        },
      },
    );
  }

  return (
    <form aria-label="Details" onSubmit={save} className="grid gap-6">
      <div className="grid gap-x-8 gap-y-5 sm:grid-cols-2 xl:grid-cols-3">
        <div className="grid min-w-0 gap-1.5 sm:col-span-2 xl:col-span-3">
          <Label htmlFor={titleId}>Title</Label>
          <Input id={titleId} value={title} onChange={(event) => setTitle(event.target.value)} />
        </div>
        {fields.map((field) => (
          <div key={field.slug} className={cn("min-w-0", field.type === "LONG_TEXT" && "sm:col-span-2 xl:col-span-3")}>
            <RecordFieldInput
              field={field}
              value={values[field.slug]}
              onChange={(next) => setValues((current) => ({ ...current, [field.slug]: next }))}
            />
          </div>
        ))}
      </div>

      {update.error ? (
        <p role="alert" className="text-sm text-destructive">
          {update.error.message}
        </p>
      ) : null}

      {/* Only while something changed: stays at the bottom of the screen while scrolling the fields. */}
      {changes > 0 ? (
        <div className="sticky bottom-0 -mx-1 flex items-center gap-2 border-t bg-background px-1 py-3 animate-in fade-in duration-150 motion-reduce:animate-none">
          <span className="mr-auto text-xs text-muted-foreground">{changes === 1 ? "1 change" : `${changes} changes`}</span>
          <AppButton type="button" variant="ghost" size="sm" disabled={update.isPending} onClick={discard}>
            Discard
          </AppButton>
          <AppButton type="submit" size="sm" loading={update.isPending}>
            Save
          </AppButton>
        </div>
      ) : null}
    </form>
  );
}
