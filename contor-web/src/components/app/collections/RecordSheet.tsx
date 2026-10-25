"use client";

import { Database, Maximize2, type LucideIcon } from "lucide-react";
import Link from "next/link";
import { Button } from "@/components/ui/button";
import { DetailSheet } from "@/components/app/DetailSheet";
import { collectionTrail } from "@/components/app/PageHeader";
import { useGetCollection } from "@/lib/api/generated/endpoints/collections/collections";
import { useGetRecord } from "@/lib/api/generated/endpoints/records/records";
import type { CollectionDto } from "@/lib/api/generated/model/collectionDto";
import { collectionTintNumber } from "@/lib/collection-tint";
import { navItem } from "@/lib/navigation";
import { recordHref, type RecordTab } from "@/lib/record-sheet";
import { RecordDetail } from "./RecordDetail";

interface RecordSheetProps {
  collectionId: number;
  recordId: number;
  /** The section icon next to the collection name; by default the calendar's or to-dos' for theirs. */
  icon?: LucideIcon;
  /** The tab shown (controlled); full screen opens the page at it. Without it the sheet keeps its own. */
  tab?: RecordTab;
  onTabChange?: (tab: RecordTab) => void;
  onOpenChange: (open: boolean) => void;
}

/** The section icon of the system collections (calendar, to-dos), the collection icon otherwise. */
function collectionIcon(collection: CollectionDto | undefined): LucideIcon {
  if (collection?.system && collection.slug === "events") return navItem("/calendar").icon;
  if (collection?.system && collection.slug === "tasks") return navItem("/tasks").icon;
  return Database;
}

/**
 * A record's detail page as the right sidebar: the same summary and tabs (details to edit, emails, reports,
 * connections) under a bar with where it lives (its collection, or the calendar / to-dos for theirs) / its title, full
 * screen (the page at the current tab) and close. Mount it while open; closing calls `onOpenChange(false)`. Usually
 * mounted by `RecordSheetHost` from the address.
 */
export function RecordSheet({ collectionId, recordId, icon, tab, onTabChange, onOpenChange }: RecordSheetProps) {
  // Shared with RecordDetail inside (same query keys), so nothing is loaded twice.
  const { data: collection } = useGetCollection(collectionId);
  const { data: record } = useGetRecord(collectionId, recordId, { query: { retry: false } });
  const title = record ? (record.title ?? `Record #${record.id}`) : "Loading…";
  // The level right above the record: its collection's table, or the calendar / to-dos page for theirs.
  const home = collection ? collectionTrail(collection).at(-1) : undefined;

  return (
    <DetailSheet
      open
      onOpenChange={onOpenChange}
      title={title}
      label={collection?.name}
      icon={icon ?? collectionIcon(collection)}
      tint={collection ? collectionTintNumber(collection.id, collection.slug) : undefined}
      trail={
        home ? (
          <Link href={home.href} className="rounded-sm underline-offset-4 outline-none hover:text-foreground hover:underline focus-visible:ring-2 focus-visible:ring-ring">
            {home.label}
          </Link>
        ) : (
          <span>…</span>
        )
      }
      actions={
        // Same variant and size as the bar's close button, so the two sit as a pair.
        <Button asChild variant="ghost" size="icon-sm">
          <Link href={recordHref(collectionId, recordId, tab)} aria-label="Open full screen" title="Open full screen">
            <Maximize2 aria-hidden="true" />
          </Link>
        </Button>
      }
    >
      <RecordDetail collectionId={collectionId} recordId={recordId} variant="sheet" tab={tab} onTabChange={onTabChange} />
    </DetailSheet>
  );
}
