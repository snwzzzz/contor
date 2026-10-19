"use client";

import { Mail, Network, Table2, UserRound, type LucideIcon } from "lucide-react";
import Image from "next/image";
import { useId, useRef, useState, type KeyboardEvent, type ReactNode } from "react";
import { ErrorMessage } from "@/components/app/ErrorMessage";
import { RefreshCommand } from "@/components/app/PageCommandButtons";
import { CommandLink, PageCommands } from "@/components/app/PageCommands";
import { collectionTrail, PageTitle, PageTrail } from "@/components/app/PageHeader";
import { domainOf, initialOf } from "@/components/app/mail/mail-format";
import { useGetCollection } from "@/lib/api/generated/endpoints/collections/collections";
import { useGetRecord, useListRecordEmails } from "@/lib/api/generated/endpoints/records/records";
import { useListRelations } from "@/lib/api/generated/endpoints/relations/relations";
import type { CollectionDto } from "@/lib/api/generated/model/collectionDto";
import type { CollectionFieldDto } from "@/lib/api/generated/model/collectionFieldDto";
import type { RecordDto } from "@/lib/api/generated/model/recordDto";
import { recordTintNumber, tintClass } from "@/lib/collection-tint";
import { bannerGradient, useDominantColor } from "@/lib/dominant-color";
import { isFreeMailDomain, useSenderPictures } from "@/lib/hooks/useSenderPictures";
import { collectionHref } from "@/lib/navigation";
import { formatValue } from "@/lib/proposal-format";
import { cn } from "@/lib/utils";
import { RecordReports } from "@/components/app/reports/RecordReports";
import { RecordConnectionsTab } from "./RecordConnectionsTab";
import { RECORD_EMAIL_PAGE_SIZE, RecordEmailsTab } from "./RecordEmailsTab";

const CREATED_FORMAT = new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "short", year: "numeric" });
const PICTURE_SIZE = 80;
/** The first page of the Emails tab: its total is the tab count (the tab reuses this query for page 1). */
const EMAIL_PAGE = { page: 0, size: RECORD_EMAIL_PAGE_SIZE } as const;

type Tab = "details" | "emails" | "connections";
const TABS: readonly { id: Tab; label: string; icon: LucideIcon }[] = [
  { id: "details", label: "Details", icon: UserRound },
  { id: "emails", label: "Emails", icon: Mail },
  { id: "connections", label: "Connections", icon: Network },
];

function hostOf(url: string): string | undefined {
  try {
    return new URL(url).hostname.replace(/^www\./, "").toLowerCase();
  } catch {
    return undefined;
  }
}

/** Mail addresses and web domains of a record, in field order: where its picture can come from. */
function pictureSources(record: RecordDto, fields: readonly CollectionFieldDto[]) {
  const addresses: string[] = [];
  const domains: string[] = [];
  for (const field of fields) {
    const value = record.values[field.slug];
    if (typeof value !== "string" || !value) continue;
    if (field.type === "EMAIL") {
      addresses.push(value);
      const domain = domainOf(value);
      if (domain && !isFreeMailDomain(domain)) domains.push(domain);
    } else if (field.type === "URL") {
      const host = hostOf(value);
      if (host) domains.push(host);
    } else if (field.slug === "domain") {
      domains.push(value.trim().toLowerCase());
    }
  }
  return { addresses, domains };
}

/**
 * One record as a full page: breadcrumbs, a contained banner in the color of its picture with the picture over its
 * edge, title | collection with its commands, and tabs for details, emails, connections.
 */
export function RecordDetail({ collectionId, recordId }: { collectionId: number; recordId: number }) {
  const { data: collection } = useGetCollection(collectionId);
  const { data: record, error } = useGetRecord(collectionId, recordId, { query: { retry: false } });

  if (error) {
    return <ErrorMessage message="This record does not exist (anymore)." />;
  }
  if (!record || !collection) {
    return <p className="text-sm text-muted-foreground">Loading…</p>;
  }
  return <RecordView collection={collection} record={record} />;
}

function RecordView({ collection, record }: { collection: CollectionDto; record: RecordDto }) {
  const fields = collection.fields.filter((field) => !field.archivedAt).toSorted((a, b) => a.position - b.position);
  const { addresses, domains } = pictureSources(record, fields);
  const pictures = useSenderPictures(domains);
  const [failedSrc, setFailedSrc] = useState<string>();
  const [tab, setTab] = useState<Tab>("details");
  const { data: emails } = useListRecordEmails(collection.id, record.id, EMAIL_PAGE, { query: { retry: false } });
  const { data: relations } = useListRelations({ collectionId: collection.id, recordId: record.id }, { query: { retry: false } });
  const candidate = addresses.map((address) => pictures.contactPhoto(address)).find(Boolean)
    ?? domains.map((domain) => pictures.logo(domain)).find(Boolean);
  const src = candidate && candidate !== failedSrc ? candidate : undefined;
  const color = useDominantColor(src);
  const title = record.title ?? `Record #${record.id}`;
  // The record's own color for its banner and initial (never its collection's), so e.g. people do not all look alike.
  const tint = tintClass(recordTintNumber(collection.id, record.id, collection.slug));
  const counts: Partial<Record<Tab, number>> = { emails: emails?.totalElements, connections: relations?.length };

  const addedBy = record.createdBy === "AI" ? "the assistant" : record.createdBy === "SYSTEM" ? "contor" : "you";

  return (
    <article className="grid gap-4">
      <PageTrail trail={collectionTrail(collection)} current={title} />

      {/* A contained banner (no full-bleed strip) with the picture over its lower edge, like a profile. */}
      <div className="grid">
        <div
          data-testid="record-banner"
          className={cn("h-28 rounded-xl transition-[background] duration-700 motion-reduce:transition-none", color ? undefined : tint)}
          style={color ? { background: bannerGradient(color) } : undefined}
        />
        <header className="flex flex-wrap items-end gap-x-4 gap-y-2 px-5">
          {src ? (
            <Image
              src={src}
              alt={title}
              width={PICTURE_SIZE}
              height={PICTURE_SIZE}
              unoptimized
              onError={() => setFailedSrc(src)}
              style={{ width: PICTURE_SIZE, height: PICTURE_SIZE }}
              className="-mt-10 shrink-0 rounded-full bg-background object-cover ring-4 ring-background"
            />
          ) : (
            <span
              data-testid="record-initial"
              aria-hidden="true"
              style={{ width: PICTURE_SIZE, height: PICTURE_SIZE }}
              className={cn("-mt-10 flex shrink-0 items-center justify-center rounded-full text-3xl font-medium ring-4 ring-background", tint)}
            >
              {initialOf(title)}
            </span>
          )}
          <div className="grid min-w-0 flex-1 gap-0.5 pt-2">
            <PageTitle title={title} subtitle={collection.name} />
            <p className="text-sm text-muted-foreground">{`Added by ${addedBy} on ${CREATED_FORMAT.format(new Date(record.createdAt))}`}</p>
          </div>
          <PageCommands>
            <RefreshCommand />
            <CommandLink href={collectionHref(collection.id)} icon={Table2} label="Table view" />
          </PageCommands>
        </header>
      </div>

      <Tabs active={tab} counts={counts} onChange={setTab}>
        {tab === "details" ? <DetailsTab fields={fields} record={record} /> : null}
        {tab === "emails" ? <RecordEmailsTab collectionId={collection.id} recordId={record.id} /> : null}
        {tab === "connections" ? <RecordConnectionsTab collectionId={collection.id} recordId={record.id} recordTitle={title} relations={relations} /> : null}
      </Tabs>
    </article>
  );
}

interface TabsProps {
  active: Tab;
  counts: Partial<Record<Tab, number>>;
  onChange: (tab: Tab) => void;
  children: ReactNode;
}

/** Underlined tabs (arrow keys move between them) and the active tab's panel. */
function Tabs({ active, counts, onChange, children }: TabsProps) {
  const id = useId();
  const buttons = useRef<(HTMLButtonElement | null)[]>([]);

  function onKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    const index = TABS.findIndex((tab) => tab.id === active);
    const next = event.key === "ArrowRight" ? index + 1 : event.key === "ArrowLeft" ? index - 1 : null;
    if (next === null) return;
    const wrapped = (next + TABS.length) % TABS.length;
    onChange(TABS[wrapped].id);
    buttons.current[wrapped]?.focus();
  }

  return (
    <>
      <div role="tablist" aria-label="Record sections" onKeyDown={onKeyDown} className="flex gap-6 overflow-x-auto border-b">
        {TABS.map((tab, index) => {
          const selected = tab.id === active;
          const count = counts[tab.id];
          return (
            <button
              key={tab.id}
              ref={(element) => {
                buttons.current[index] = element;
              }}
              id={`${id}-${tab.id}`}
              type="button"
              role="tab"
              aria-selected={selected}
              aria-controls={`${id}-${tab.id}-panel`}
              tabIndex={selected ? 0 : -1}
              onClick={() => onChange(tab.id)}
              className={cn(
                "-mb-px flex items-center gap-2 border-b-2 py-3 text-sm whitespace-nowrap transition-colors",
                selected ? "border-primary font-medium text-primary" : "border-transparent text-muted-foreground hover:text-foreground",
              )}
            >
              <tab.icon className="size-4" aria-hidden="true" />
              {tab.label}
              {count ? <span className="rounded-full bg-muted px-1.5 text-xs text-muted-foreground tabular-nums">{count}</span> : null}
            </button>
          );
        })}
      </div>
      <div role="tabpanel" id={`${id}-${active}-panel`} aria-labelledby={`${id}-${active}`} className="py-5">
        {children}
      </div>
    </>
  );
}

function DetailsTab({ fields, record }: { fields: readonly CollectionFieldDto[]; record: RecordDto }) {
  return (
    <div className="grid gap-6">
      <RecordReports collectionId={record.collectionId} recordId={record.id} />
      <ul aria-label="Fields" className="grid gap-x-10 gap-y-5 rounded-xl border p-6 sm:grid-cols-2 xl:grid-cols-3">
        {fields.map((field) => (
          <li key={field.slug} className={cn("grid min-w-0 gap-0.5", field.type === "LONG_TEXT" && "sm:col-span-2 xl:col-span-3")}>
            <span className="text-xs text-muted-foreground">{field.name}</span>
            <span className="min-w-0 text-sm break-words">
              <FieldValue field={field} value={record.values[field.slug]} />
            </span>
          </li>
        ))}
      </ul>
    </div>
  );
}

const LINK = "underline-offset-4 hover:underline";

function FieldValue({ field, value }: { field: CollectionFieldDto; value: unknown }) {
  if (value === null || value === undefined || value === "") {
    return <span className="text-muted-foreground">–</span>;
  }
  if (typeof value === "string") {
    if (field.type === "EMAIL") return <a href={`mailto:${value}`} className={LINK}>{value}</a>;
    if (field.type === "PHONE") return <a href={`tel:${value.replace(/[^\d+]/g, "")}`} className={LINK}>{value}</a>;
    if (field.type === "URL") {
      const label = value.replace(/^https?:\/\//, "").replace(/\/$/, "");
      return <a href={value} target="_blank" rel="noreferrer" className={LINK}>{label}</a>;
    }
    if (field.type === "LONG_TEXT") return <span className="whitespace-pre-line">{value}</span>;
    if (field.type === "ENUM" || field.type === "STATUS") return <span className="rounded-md bg-muted px-1.5 py-0.5 text-xs">{value}</span>;
  }
  return <>{formatValue(value)}</>;
}
