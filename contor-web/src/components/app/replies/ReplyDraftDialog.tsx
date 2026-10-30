"use client";

import { X } from "lucide-react";
import { useId, useState, type KeyboardEvent } from "react";
import { AppButton } from "@/components/app/AppButton";
import { Dialog, DialogContent, DialogDescription, DialogTitle } from "@/components/ui/dialog";
import { FIELD, FIELD_FOCUS_WITHIN, Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { placeholdersIn, recipientLabel, type ReplyRecipient, type ReplyText } from "@/lib/reply-draft";
import { cn } from "@/lib/utils";

interface ReplyDraftDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** The draft as the assistant (or you, last time) wrote it. */
  initial: ReplyText;
  title?: string;
  busy?: boolean;
  /** Keeps the (edited) text; nothing is sent. */
  onKeep: (draft: ReplyText) => void;
}

/**
 * Edit a reply draft before keeping it: recipients, subject and text. "Keep" waits until every `[…]` placeholder is
 * filled in; "Keep anyway" keeps it with the placeholders left. Nothing is ever sent from here.
 */
export function ReplyDraftDialog({ open, onOpenChange, initial, title = "Edit reply draft", busy = false, onKeep }: ReplyDraftDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="flex max-h-[min(90svh,48rem)] w-[min(44rem,calc(100%-2rem))] max-w-none flex-col gap-4 overflow-y-auto sm:max-w-none">
        <div className="grid gap-1">
          <DialogTitle>{title}</DialogTitle>
          <DialogDescription>Drafts are never sent. Keep it, then copy it or open it in your mail app.</DialogDescription>
        </div>
        {/* Mounted only while open, so every opening starts from `initial`. */}
        {open ? <ReplyDraftForm initial={initial} busy={busy} onCancel={() => onOpenChange(false)} onKeep={onKeep} /> : null}
      </DialogContent>
    </Dialog>
  );
}

interface ReplyDraftFormProps {
  initial: ReplyText;
  busy: boolean;
  onCancel: () => void;
  onKeep: (draft: ReplyText) => void;
}

function ReplyDraftForm({ initial, busy, onCancel, onKeep }: ReplyDraftFormProps) {
  const [draft, setDraft] = useState<ReplyText>(initial);
  const subjectId = useId();
  const bodyId = useId();
  const left = placeholdersIn(draft.body);
  const complete = draft.to.length > 0 && draft.body.trim() !== "";

  return (
    <div className="grid gap-4">
      <RecipientField label="To" recipients={draft.to} onChange={(to) => setDraft((current) => ({ ...current, to }))} />
      <RecipientField label="Cc" recipients={draft.cc} onChange={(cc) => setDraft((current) => ({ ...current, cc }))} />
      <div className="grid gap-1.5">
        <label htmlFor={subjectId} className="text-sm font-medium">
          Subject
        </label>
        <Input id={subjectId} value={draft.subject} onChange={(event) => setDraft((current) => ({ ...current, subject: event.target.value }))} />
      </div>
      <div className="grid gap-1.5">
        <label htmlFor={bodyId} className="text-sm font-medium">
          Text
        </label>
        <Textarea
          id={bodyId}
          className="min-h-56"
          value={draft.body}
          aria-invalid={left.length > 0 ? true : undefined}
          onChange={(event) => setDraft((current) => ({ ...current, body: event.target.value }))}
        />
        {left.length > 0 ? (
          <div className="flex flex-wrap items-center gap-1.5 text-xs" role="status">
            <span className="text-muted-foreground">Still to fill in:</span>
            {left.map((placeholder) => (
              <span key={placeholder} className="rounded bg-warning/20 px-1.5 py-0.5 font-medium text-foreground ring-1 ring-warning/50">
                {placeholder}
              </span>
            ))}
          </div>
        ) : null}
      </div>
      <div className="flex flex-wrap justify-end gap-2">
        <AppButton variant="ghost" disabled={busy} onClick={onCancel}>
          Cancel
        </AppButton>
        {left.length > 0 ? (
          <AppButton variant="outline" disabled={!complete} loading={busy} onClick={() => onKeep(draft)}>
            Keep anyway
          </AppButton>
        ) : null}
        <AppButton disabled={!complete || left.length > 0} loading={busy && left.length === 0} onClick={() => onKeep(draft)}>
          Keep
        </AppButton>
      </div>
    </div>
  );
}

interface RecipientFieldProps {
  label: string;
  recipients: ReplyRecipient[];
  onChange: (recipients: ReplyRecipient[]) => void;
}

const ADDRESS = /^[^\s@]+@[^\s@]+$/;

/** Recipients as removable chips plus a field to add an address (Enter or comma). */
function RecipientField({ label, recipients, onChange }: RecipientFieldProps) {
  const id = useId();
  const [text, setText] = useState("");

  const add = () => {
    const address = text.trim().replace(/,$/, "");
    if (!ADDRESS.test(address)) return;
    if (!recipients.some((recipient) => recipient.address?.toLowerCase() === address.toLowerCase())) {
      onChange([...recipients, { address }]);
    }
    setText("");
  };
  const onKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === "Enter" || event.key === ",") {
      event.preventDefault();
      add();
    }
  };

  return (
    <div className="grid gap-1.5">
      <label htmlFor={id} className="text-sm font-medium">
        {label}
      </label>
      <div className={cn("flex flex-wrap items-center gap-1.5 px-2 py-1", FIELD, FIELD_FOCUS_WITHIN)}>
        {recipients.map((recipient) => (
          <span key={recipient.address} className="flex items-center gap-1 rounded-md bg-muted px-2 py-0.5 text-xs">
            {recipientLabel(recipient)}
            <button
              type="button"
              aria-label={`Remove ${recipient.address} from ${label}`}
              className="rounded text-muted-foreground hover:text-foreground"
              onClick={() => onChange(recipients.filter((other) => other !== recipient))}
            >
              <X className="size-3" aria-hidden="true" />
            </button>
          </span>
        ))}
        <Input
          id={id}
          // Borderless inside the chip box, which carries the field look.
          className="h-6 w-auto min-w-40 flex-1 border-0 bg-transparent p-0 shadow-none focus-visible:ring-0 dark:bg-transparent"
          placeholder="Add address"
          value={text}
          onChange={(event) => setText(event.target.value)}
          onKeyDown={onKeyDown}
          onBlur={add}
        />
      </div>
    </div>
  );
}
