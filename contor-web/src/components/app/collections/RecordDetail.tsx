"use client";

import { ExternalLink, FileText, Mail, Network, Phone, Sparkles, Table2, UserRound, type LucideIcon } from "lucide-react";
import Image from "next/image";
import { useId, useRef, useState, type KeyboardEvent, type ReactNode } from "react";
import { ErrorMessage } from "@/components/app/ErrorMessage";
import { Button } from "@/components/ui/button";
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
import type { RecordTab } from "@/lib/record-sheet";
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

type Tab = RecordTab;
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

/** The first value of a field of `type`, e.g. the record's email address. */
function firstValue(record: RecordDto, fields: readonly CollectionFieldDto[], type: string): string | undefined {
  for (const field of fields) {
    const value = record.values[field.slug];
    if (field.type === type && typeof value === "string" && value.trim() !== "") return value.trim();
  }
  return undefined;
}

/** The line under the title: the record's website, else its email address. */
function contactLink(website: string | undefined, email: string | undefined): { href: string; text: string; external: boolean } | null {
  if (website) return { href: website, text: hostOf(website) ?? website, external: true };
  if (email) return { href: `mailto:${email}`, text: email, external: false };
  return null;
}

/** `page`: the record's own page. `sheet`: the same inside the detail sidebar, whose bar already names the collection. */
type Variant = "page" | "sheet";
const SHEET_PICTURE_SIZE = 48;

interface RecordDetailProps {
  collectionId: number;
  recordId: number;
  variant?: Variant;
  /** The tab shown, when the caller keeps it (in the address); without it the record keeps its own, from Details. */
  tab?: RecordTab;
  onTabChange?: (tab: RecordTab) => void;
}

/**
 * One record: breadcrumbs, the picture next to title | collection with its commands, and tabs for details, emails,
 * reports, connections. As a `sheet` (the detail sidebar) it keeps the picture, collection, "Added by" and all tabs and
 * leaves out what the sidebar has itself or what belongs to a page (breadcrumbs, title, commands).
 */
export function RecordDetail({ collectionId, recordId, variant = "page", tab, onTabChange }: RecordDetailProps) {
  const { data: collection } = useGetCollection(collectionId);
  const { data: record, error } = useGetRecord(collectionId, recordId, { query: { retry: false } });

  if (error) {
    return <ErrorMessage message="This record does not exist (anymore)." />;
  }
  if (!record || !collection) {
    return <LoadingDetail label="Loading record" fields={6} />;
  }
  return <RecordView collection={collection} record={record} variant={variant} tab={tab} onTabChange={onTabChange} />;
}

interface RecordViewProps {
  collection: CollectionDto;
  record: RecordDto;
  variant: Variant;
  tab?: RecordTab;
  onTabChange?: (tab: RecordTab) => void;
}

function RecordView({ collection, record, variant, tab: controlledTab, onTabChange }: RecordViewProps) {
  const sheet = variant === "sheet";
  const pictureSize = sheet ? SHEET_PICTURE_SIZE : PICTURE_SIZE;
  const fields = collection.fields.filter((field) => !field.archivedAt).toSorted((a, b) => a.position - b.position);
  const { addresses, domains } = pictureSources(record, fields);
  const pictures = useSenderPictures(domains);
  const [failedSrc, setFailedSrc] = useState<string>();
  const [ownTab, setOwnTab] = useState<Tab>("details");
  const tab = controlledTab ?? ownTab;
  const setTab = (next: Tab) => {
    setOwnTab(next);
    onTabChange?.(next);
  };
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

  // The reports workspace takes the rest of the height (the page's from lg, the sidebar's always), so its divider runs to
  // the bottom.
  const fill = tab === "reports";
  const addedBy = record.createdBy === "AI" ? "the assistant" : record.createdBy === "SYSTEM" ? "contor" : "you";
  const added = `by ${addedBy} on ${CREATED_FORMAT.format(new Date(record.createdAt))}`;
  const email = firstValue(record, fields, "EMAIL");
  const phone = firstValue(record, fields, "PHONE");
  const contact = contactLink(firstValue(record, fields, "URL"), email);

  return (
    // On the Reports tab the page is exactly the screen's height: a column whose tab panel takes the rest (no page scroll).
    <article className={cn("grid gap-3", fill && (sheet ? "flex min-h-full flex-col" : "lg:flex lg:min-h-0 lg:flex-1 lg:flex-col"))}>
      {sheet ? null : <PageTrail trail={collectionTrail(collection)} current={title} />}

      {/* The profile: the picture next to the title and the contact line (and the page's commands), the quick actions
          below. */}
      <div data-testid="record-profile" className="grid shrink-0 gap-4">
        <header className={cn("flex flex-wrap items-center gap-y-2", sheet ? "gap-x-3" : "gap-x-4")}>
          {src ? (
            <Image
              src={src}
              alt={title}
              width={pictureSize}
              height={pictureSize}
              unoptimized
              onError={() => setFailedSrc(src)}
              style={{ width: pictureSize, height: pictureSize }}
              className="shrink-0 rounded-lg bg-muted object-cover"
            />
          ) : (
            <span
              data-testid="record-initial"
              aria-hidden="true"
              style={{ width: pictureSize, height: pictureSize }}
              className={cn("flex shrink-0 items-center justify-center rounded-lg font-medium", sheet ? "text-lg" : "text-2xl", tint)}
            >
              {initialOf(title)}
            </span>
          )}
          {sheet ? (
            // The sidebar's bar names the collection; here the title (the bar's last crumb is its heading).
            <div className="grid min-w-0 flex-1 gap-0.5">
              <p className="text-lg leading-snug font-semibold tracking-tight [overflow-wrap:anywhere]">{title}</p>
              <ContactLine contact={contact} />
            </div>
          ) : (
            <>
              <div className="grid min-w-0 flex-1 gap-0.5">
                <PageTitle title={title} subtitle={collection.name} />
                <ContactLine contact={contact} />
              </div>
              <PageCommands>
                <RefreshCommand />
                <CommandLink href={collectionHref(collection.id)} icon={Table2} label="Table view" />
              </PageCommands>
            </>
          )}
        </header>
        <QuickActions email={email} phone={phone} onAsk={() => setTab("reports")} />
      </div>

      <Tabs active={tab} counts={counts} fill={fill} compact={sheet} onChange={setTab}>
        {tab === "details" ? <RecordDetailsForm fields={fields} record={record} added={added} /> : null}
        {tab === "emails" ? <RecordEmailsTab collectionId={collection.id} recordId={record.id} /> : null}
        {tab === "reports" ? <RecordReports collectionId={collection.id} recordId={record.id} recordTitle={title} /> : null}
        {tab === "connections" ? <RecordConnectionsTab collectionId={collection.id} recordId={record.id} recordTitle={title} relations={relations} /> : null}
      </Tabs>
    </article>
  );
}

/** The website or email address under the title, muted, as a link. */
function ContactLine({ contact }: { contact: { href: string; text: string; external: boolean } | null }) {
  if (!contact) return null;
  return (
    <a
      href={contact.href}
      {...(contact.external ? { target: "_blank", rel: "noreferrer" } : {})}
      className="flex w-fit min-w-0 items-center gap-1.5 text-sm text-muted-foreground underline-offset-4 hover:text-foreground hover:underline"
    >
      {contact.external ? <ExternalLink aria-hidden="true" className="size-3.5 shrink-0" /> : <Mail aria-hidden="true" className="size-3.5 shrink-0" />}
      <span className="truncate">{contact.text}</span>
    </a>
  );
}

/** Small outline buttons for what the record allows: write to it, call it, ask the assistant about it (Reports). */
function QuickActions({ email, phone, onAsk }: { email?: string; phone?: string; onAsk: () => void }) {
  return (
    <div role="group" aria-label="Quick actions" className="flex flex-wrap gap-2">
      {email ? (
        <Button asChild variant="outline" size="sm">
          <a href={`mailto:${email}`}>
            <Mail aria-hidden="true" data-icon="inline-start" />
            Email
          </a>
        </Button>
      ) : null}
      {phone ? (
        <Button asChild variant="outline" size="sm">
          <a href={`tel:${phone.replace(/[^\d+]/g, "")}`}>
            <Phone aria-hidden="true" data-icon="inline-start" />
            Call
          </a>
        </Button>
      ) : null}
      <Button type="button" variant="outline" size="sm" onClick={onAsk}>
        <Sparkles aria-hidden="true" data-icon="inline-start" />
        Ask
      </Button>
    </div>
  );
}

interface TabsProps {
  active: Tab;
  counts: Partial<Record<Tab, number>>;
  /** The panel takes the rest of the height (the page's on large screens, the sidebar's always) and has no top padding. */
  fill: boolean;
  /** In the sidebar: closer together, so all four fit its default width, and across its full width. */
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
        className={cn("flex shrink-0 overflow-x-auto overflow-y-hidden shadow-[inset_0_-1px_0_var(--border)]", compact ? "-mx-4 gap-4 px-4" : "gap-6")}
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
              <tab.icon className="size-4" aria-hidden="true" strokeWidth={1.75} />
              {tab.label}
              {count ? <span className="rounded-full bg-muted px-1.5 text-xs text-muted-foreground tabular-nums">{count}</span> : null}
            </button>
          );
        })}
      </div>
      <div role="tabpanel" id={`${id}-${active}-panel`} aria-labelledby={`${id}-${active}`}
        className={cn(
          "py-5",
          fill && (compact ? "flex flex-1 flex-col pb-0 lg:pt-0" : "lg:flex lg:min-h-0 lg:flex-1 lg:flex-col lg:pt-0 lg:pb-0"),
        )}
      >
        {children}
      </div>
    </>
  );
}

