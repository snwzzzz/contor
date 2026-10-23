"use client";

import { FileText, Mail, Network, Table2, UserRound, type LucideIcon } from "lucide-react";
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
import { isFreeMailDomain, useSenderPictures } from "@/lib/hooks/useSenderPictures";
import { collectionHref } from "@/lib/navigation";
import { cn } from "@/lib/utils";
import { RecordReports, useRecordReports } from "@/components/app/reports/RecordReports";
import { RecordConnectionsTab } from "./RecordConnectionsTab";
import { RecordDetailsForm } from "./RecordDetailsForm";
import { RECORD_EMAIL_PAGE_SIZE, RecordEmailsTab } from "./RecordEmailsTab";
import { LoadingDetail } from "@/components/app/LoadingSkeletons";

const CREATED_FORMAT = new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "short", year: "numeric" });
const PICTURE_SIZE = 64;
/** The first page of the Emails tab: its total is the tab count (the tab reuses this query for page 1). */
const EMAIL_PAGE = { page: 0, size: RECORD_EMAIL_PAGE_SIZE } as const;

type Tab = "details" | "emails" | "reports" | "connections";
const TABS: readonly { id: Tab; label: string; icon: LucideIcon }[] = [
  { id: "details", label: "Details", icon: UserRound },
  { id: "emails", label: "Emails", icon: Mail },
  { id: "reports", label: "Reports", icon: FileText },
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

/** `page`: the record's own page. `sheet`: the same inside the detail sidebar, whose header already has the title. */
type Variant = "page" | "sheet";
const SHEET_PICTURE_SIZE = 40;

/**
 * One record: breadcrumbs, the picture next to title | collection with its commands, and tabs for details, emails,
 * reports, connections. As a `sheet` (the detail sidebar) it keeps the picture, collection, "Added by" and all tabs and
 * leaves out what the sidebar has itself or what belongs to a page (breadcrumbs, title, commands).
 */
export function RecordDetail({ collectionId, recordId, variant = "page" }: { collectionId: number; recordId: number; variant?: Variant }) {
  const { data: collection } = useGetCollection(collectionId);
  const { data: record, error } = useGetRecord(collectionId, recordId, { query: { retry: false } });

  if (error) {
    return <ErrorMessage message="This record does not exist (anymore)." />;
  }
  if (!record || !collection) {
    return <LoadingDetail label="Loading record" fields={6} />;
  }
  return <RecordView collection={collection} record={record} variant={variant} />;
}

function RecordView({ collection, record, variant }: { collection: CollectionDto; record: RecordDto; variant: Variant }) {
  const sheet = variant === "sheet";
  const pictureSize = sheet ? SHEET_PICTURE_SIZE : PICTURE_SIZE;
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
  const title = record.title ?? `Record #${record.id}`;
  // The record's own color for its initial (never its collection's), so e.g. people do not all look alike.
  const tint = tintClass(recordTintNumber(collection.id, record.id, collection.slug));
  const { data: reports } = useRecordReports(collection.id, record.id);
  const counts: Partial<Record<Tab, number>> = { emails: emails?.totalElements, reports: reports?.length, connections: relations?.length };

  // The reports workspace takes the page's full height; in the sidebar it simply scrolls with the rest.
  const fill = tab === "reports" && !sheet;
  const addedBy = record.createdBy === "AI" ? "the assistant" : record.createdBy === "SYSTEM" ? "contor" : "you";
  const addedLine = `Added by ${addedBy} on ${CREATED_FORMAT.format(new Date(record.createdAt))}`;

  return (
    // On the Reports tab the page is exactly the screen's height: a column whose tab panel takes the rest (no page scroll).
    <article className={cn("grid gap-3", fill && "lg:flex lg:min-h-0 lg:flex-1 lg:flex-col")}>
      {sheet ? null : <PageTrail trail={collectionTrail(collection)} current={title} />}

      {/* The profile: picture next to title | collection and the commands; room below before the tabs. */}
      <header className={cn("flex shrink-0 flex-wrap items-center gap-y-2", sheet ? "gap-x-3" : "gap-x-4")}>
        {src ? (
          <Image
            src={src}
            alt={title}
            width={pictureSize}
            height={pictureSize}
            unoptimized
            onError={() => setFailedSrc(src)}
            style={{ width: pictureSize, height: pictureSize }}
            className="shrink-0 rounded-full bg-muted object-cover"
          />
        ) : (
          <span
            data-testid="record-initial"
            aria-hidden="true"
            style={{ width: pictureSize, height: pictureSize }}
            className={cn("flex shrink-0 items-center justify-center rounded-full font-medium", sheet ? "text-base" : "text-2xl", tint)}
          >
            {initialOf(title)}
          </span>
        )}
        {sheet ? (
          // The sidebar's header carries the collection and the title; here only who added it.
          <p className="min-w-0 flex-1 text-sm text-muted-foreground">{addedLine}</p>
        ) : (
          <>
            <div className="grid min-w-0 flex-1 gap-0.5">
              <PageTitle title={title} subtitle={collection.name} />
              <p className="text-sm text-muted-foreground">{addedLine}</p>
            </div>
            <PageCommands>
              <RefreshCommand />
              <CommandLink href={collectionHref(collection.id)} icon={Table2} label="Table view" />
            </PageCommands>
          </>
        )}
      </header>

      <Tabs active={tab} counts={counts} fill={fill} compact={sheet} onChange={setTab}>
        {tab === "details" ? <RecordDetailsForm fields={fields} record={record} /> : null}
        {tab === "emails" ? <RecordEmailsTab collectionId={collection.id} recordId={record.id} /> : null}
        {tab === "reports" ? <RecordReports collectionId={collection.id} recordId={record.id} recordTitle={title} /> : null}
        {tab === "connections" ? <RecordConnectionsTab collectionId={collection.id} recordId={record.id} recordTitle={title} relations={relations} /> : null}
      </Tabs>
    </article>
  );
}

interface TabsProps {
  active: Tab;
  counts: Partial<Record<Tab, number>>;
  /** The panel takes the rest of the page's height (large screens) instead of growing with its content. */
  fill: boolean;
  /** In the sidebar: labels without icons and closer together, so all four fit its default width. */
  compact: boolean;
  onChange: (tab: Tab) => void;
  children: ReactNode;
}

/** Underlined tabs (arrow keys move between them) and the active tab's panel. */
function Tabs({ active, counts, fill, compact, onChange, children }: TabsProps) {
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
      {/* The hairline is an inset shadow under the tabs, so the active underline needs no -mb-px (which made the row
          1px taller than itself and showed a vertical scrollbar). */}
      <div
        role="tablist"
        aria-label="Record sections"
        onKeyDown={onKeyDown}
        className={cn("flex shrink-0 overflow-x-auto overflow-y-hidden shadow-[inset_0_-1px_0_var(--border)]", compact ? "gap-4" : "gap-6")}
      >
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
                "flex items-center border-b-2 text-sm whitespace-nowrap",
                compact ? "gap-1.5 py-2.5" : "gap-2 py-3",
                " outline-none transition-colors focus-visible:rounded-sm focus-visible:ring-2 focus-visible:ring-ring",
                selected ? "border-primary font-medium text-primary" : "border-transparent text-muted-foreground hover:text-foreground",
              )}
            >
              {compact ? null : <tab.icon className="size-4" aria-hidden="true" />}
              {tab.label}
              {count ? <span className="rounded-full bg-muted px-1.5 text-xs text-muted-foreground tabular-nums">{count}</span> : null}
            </button>
          );
        })}
      </div>
      <div role="tabpanel" id={`${id}-${active}-panel`} aria-labelledby={`${id}-${active}`}
        className={cn("py-5", fill && "lg:flex lg:min-h-0 lg:flex-1 lg:flex-col lg:pt-4 lg:pb-0")}
      >
        {children}
      </div>
    </>
  );
}

