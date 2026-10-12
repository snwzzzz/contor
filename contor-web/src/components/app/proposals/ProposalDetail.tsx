"use client";

import Link from "next/link";
import { useState, type ReactNode } from "react";
import { AppButton } from "@/components/app/AppButton";
import { recordHref } from "@/components/app/collections/CollectionTable";
import { RecordFieldInput } from "@/components/app/collections/RecordFieldInput";
import { DetailSheet } from "@/components/app/DetailSheet";
import { EmailBody } from "@/components/app/mail/EmailReader";
import { ReplySuggestionCard } from "@/components/app/replies/ReplySuggestionCard";
import { useGetEmail } from "@/lib/api/generated/endpoints/mail/mail";
import { useGetRecord } from "@/lib/api/generated/endpoints/records/records";
import type { CollectionDto } from "@/lib/api/generated/model/collectionDto";
import type { ProposalDto } from "@/lib/api/generated/model/proposalDto";
import type { Description } from "@/lib/proposal-describe";
import { formatValue, humanize, proposalTitle, type ProposalPayload } from "@/lib/proposal-format";
import type { ReplyDraftPayload } from "@/lib/reply-draft";
import { Input } from "@/components/ui/input";

const CREATED = new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit" });
const RECEIVED = new Intl.DateTimeFormat("en-GB", { weekday: "short", day: "numeric", month: "short", year: "numeric" });

interface ProposalDetailProps {
  proposal: ProposalDto;
  description: Description;
  /** The target collection, when the proposal writes a record (its fields make the values editable). */
  collection?: CollectionDto;
  busy: boolean;
  /** `payload` is the edited payload when the values were changed before approving. */
  onDecide: (decision: "APPROVE" | "REJECT", payload?: Record<string, unknown>) => void;
  onClose: () => void;
  /** Button texts (findings say Keep / Dismiss). */
  approveLabel?: string;
  rejectLabel?: string;
}

/** Everything the assistant proposes with the reasons and the email it comes from; decide from here. */
export function ProposalDetail({
  proposal, description, collection, busy, onDecide, onClose, approveLabel = "Approve", rejectLabel = "Reject",
}: ProposalDetailProps) {
  const payload = proposal.payload as ProposalPayload;
  const isReply = proposal.kind === "DRAFT_REPLY";
  const title = isReply ? ((proposal.payload as unknown as ReplyDraftPayload).subject || "(No subject)") : proposalTitle(payload);
  const confidence = proposal.confidence != null ? Math.round(proposal.confidence * 100) : null;
  const canEdit = (proposal.kind === "CREATE_RECORD" || proposal.kind === "UPDATE_RECORD") && (collection?.fields.length ?? 0) > 0;
  const [draft, setDraft] = useState<Draft | null>(null);
  const edited = draft !== null && (draft.title !== (payload.title ?? "") || !same(draft.values, payload.values ?? {}));

  function approve() {
    if (!draft || !edited) {
      onDecide("APPROVE");
      return;
    }
    const titled = draft.title.trim() || payload.title !== undefined ? { title: draft.title.trim() } : {};
    onDecide("APPROVE", { ...proposal.payload, ...titled, values: draft.values });
  }

  return (
    <DetailSheet
      open
      onOpenChange={(open) => !open && onClose()}
      title={title}
      label={description.label}
      icon={description.icon}
      tint={description.tint}
      aside={confidence !== null ? <span className="text-xs tabular-nums text-muted-foreground" title="How sure the assistant is">{confidence}%</span> : null}
      footer={
        isReply ? null : (
          <>
            {canEdit && draft === null ? (
              <AppButton variant="ghost" className="mr-auto" onClick={() => setDraft({ title: payload.title ?? "", values: { ...payload.values } })}>
                Edit
              </AppButton>
            ) : null}
            {draft !== null ? (
              <AppButton variant="ghost" className="mr-auto" disabled={busy} onClick={() => setDraft(null)}>Undo changes</AppButton>
            ) : null}
            <AppButton variant="ghost" disabled={busy} onClick={() => onDecide("REJECT")}>{rejectLabel}</AppButton>
            <AppButton loading={busy} onClick={approve}>{approveLabel}</AppButton>
          </>
        )
      }
    >
      <div className="grid gap-5">
        {isReply ? (
          <ReplySuggestionCard proposal={proposal} full label="Proposed reply" />
        ) : (
          draft !== null && collection ? (
            <EditValues collection={collection} draft={draft} onChange={setDraft} />
          ) : (
            <Changes proposal={proposal} payload={payload} collectionName={collection?.name} />
          )
        )}

        {proposal.reasoning ? (
          <Section heading="Why">
            <p className="text-sm leading-relaxed text-muted-foreground">{proposal.reasoning}</p>
          </Section>
        ) : null}

        {proposal.sourceEmailId != null ? <SourceEmail emailId={proposal.sourceEmailId} /> : null}

        <p className="text-xs text-muted-foreground">
          Proposed {CREATED.format(new Date(proposal.createdAt))}
          {proposal.model ? ` by ${proposal.model}` : ""}
          {proposal.promptVersion ? ` (${proposal.promptVersion})` : ""}
        </p>
      </div>
    </DetailSheet>
  );
}

function Section({ heading, children }: { heading: string; children: ReactNode }) {
  return (
    <section className="grid gap-2">
      <h3 className="text-xs font-medium tracking-wide text-muted-foreground uppercase">{heading}</h3>
      {children}
    </section>
  );
}

function same(a: unknown, b: unknown): boolean {
  return JSON.stringify(a) === JSON.stringify(b);
}

interface Draft {
  title: string;
  values: Record<string, unknown>;
}

/**
 * The proposal's values as inputs of the collection's fields (approving sends them as the edited payload, stored as
 * EDITED_AND_APPROVED). Values without a field of the collection are kept as they are and shown as text.
 */
function EditValues({ collection, draft, onChange }: { collection: CollectionDto; draft: Draft; onChange: (draft: Draft) => void }) {
  const bySlug = new Map(collection.fields.filter((field) => !field.archivedAt).map((field) => [field.slug, field]));
  return (
    <Section heading="Edit before approving">
      <p className="text-sm text-muted-foreground">In {collection.name}</p>
      <Input aria-label="Title" value={draft.title} onChange={(event) => onChange({ ...draft, title: event.target.value })} />
      <div className="grid gap-3">
        {Object.entries(draft.values).map(([key, value]) => {
          const field = bySlug.get(key);
          return field ? (
            <RecordFieldInput key={key} field={field} value={value} onChange={(next) => onChange({ ...draft, values: { ...draft.values, [key]: next } })} />
          ) : (
            <div key={key} className="grid gap-1 text-sm">
              <span className="font-medium">{humanize(key)}</span>
              <span className="text-muted-foreground [overflow-wrap:anywhere]">{formatValue(value)}</span>
            </div>
          );
        })}
      </div>
    </Section>
  );
}

/** What would be stored: all values; for an update, the current value next to the new one (unchanged ones folded). */
function Changes({ proposal, payload, collectionName }: { proposal: ProposalDto; payload: ProposalPayload; collectionName?: string }) {
  const [showAll, setShowAll] = useState(false);
  const isUpdate = proposal.kind === "UPDATE_RECORD" && payload.collectionId != null && payload.recordId != null;
  const { data: record } = useGetRecord(payload.collectionId ?? 0, payload.recordId ?? 0, { query: { enabled: isUpdate, retry: false } });

  const values = Object.entries(payload.values ?? {}).filter(([, value]) => isUpdate || (value !== null && value !== ""));
  const fields = (payload.fields ?? []).map((field) => field.name).filter((name): name is string => Boolean(name));
  const rows = values.map(([key, value]) => ({ key, next: value, before: record?.values?.[key], changed: !record || !same(record.values?.[key], value) }));
  const compare = isUpdate && record !== undefined;
  const visible = compare && !showAll ? rows.filter((row) => row.changed) : rows;
  const folded = rows.length - visible.length;

  return (
    <Section heading={isUpdate ? "Changes" : "Would store"}>
      {collectionName ? <p className="text-sm text-muted-foreground">In {collectionName}</p> : null}
      {visible.length > 0 ? (
        <dl className="grid grid-cols-[auto_minmax(0,1fr)] gap-x-4 gap-y-2 text-sm">
          {visible.map((row) => (
            <div key={row.key} className="contents">
              <dt className="text-muted-foreground">{humanize(row.key)}</dt>
              <dd className="grid min-w-0 gap-0.5">
                {compare && row.changed ? (
                  <>
                    <span className="text-muted-foreground line-through [overflow-wrap:anywhere]">{formatValue(row.before)}</span>
                    <span className="rounded bg-primary/5 px-1 font-medium [overflow-wrap:anywhere]">{formatValue(row.next)}</span>
                  </>
                ) : (
                  <span className="whitespace-pre-line [overflow-wrap:anywhere]">{formatValue(row.next)}</span>
                )}
              </dd>
            </div>
          ))}
        </dl>
      ) : null}
      {fields.length > 0 ? (
        <div className="grid gap-1 text-sm">
          <span className="text-muted-foreground">Fields</span>
          <ul className="list-disc pl-5">
            {fields.map((name) => <li key={name}>{name}</li>)}
          </ul>
        </div>
      ) : null}
      {folded > 0 ? (
        <button type="button" className="w-fit text-xs text-muted-foreground hover:text-foreground" onClick={() => setShowAll(true)}>
          Show {folded} unchanged {folded === 1 ? "field" : "fields"}
        </button>
      ) : null}
      {isUpdate ? (
        <Link href={recordHref(payload.collectionId ?? 0, payload.recordId ?? 0)} className="w-fit text-xs text-muted-foreground hover:text-foreground">
          Open the record
        </Link>
      ) : null}
    </Section>
  );
}

/** The email the proposal was made from: sender, date, subject; the whole mail on demand. */
function SourceEmail({ emailId }: { emailId: number }) {
  const [open, setOpen] = useState(false);
  const { data: email, isLoading } = useGetEmail(emailId, { query: { retry: false } });
  if (isLoading) return <p className="text-sm text-muted-foreground">Loading the email…</p>;
  if (!email) return null;

  return (
    <Section heading="From this email">
      <div className="grid min-w-0 gap-1 rounded-lg border bg-muted/30 p-3 text-sm">
        <div className="flex min-w-0 items-baseline justify-between gap-2">
          <span className="min-w-0 truncate font-medium">{email.fromName || email.fromAddress}</span>
          <span className="shrink-0 text-xs text-muted-foreground">{RECEIVED.format(new Date(email.receivedAt))}</span>
        </div>
        <span className="[overflow-wrap:anywhere]">{email.subject || "(No subject)"}</span>
        {!open && email.snippet ? <span className="line-clamp-2 text-xs text-muted-foreground [overflow-wrap:anywhere]">{email.snippet}</span> : null}
      </div>
      {open ? <EmailBody email={email} /> : null}
      <div className="flex gap-3">
        <button type="button" className="text-xs text-muted-foreground hover:text-foreground" onClick={() => setOpen((value) => !value)}>
          {open ? "Hide email" : "Show email"}
        </button>
        <Link href={`/inbox?email=${emailId}`} className="text-xs text-muted-foreground hover:text-foreground">
          Open in inbox
        </Link>
      </div>
    </Section>
  );
}
