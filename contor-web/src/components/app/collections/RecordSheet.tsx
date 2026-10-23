"use client";

import { Database, Maximize2, type LucideIcon } from "lucide-react";
import Link from "next/link";
import { Button } from "@/components/ui/button";
import { DetailSheet } from "@/components/app/DetailSheet";
import { useGetCollection } from "@/lib/api/generated/endpoints/collections/collections";
import { useGetRecord } from "@/lib/api/generated/endpoints/records/records";
import { collectionTintNumber } from "@/lib/collection-tint";
import { recordHref } from "./CollectionTable";
import { RecordDetail } from "./RecordDetail";

interface RecordSheetProps {
  collectionId: number;
  recordId: number;
  /** The section icon next to the collection name (e.g. the calendar's). */
  icon?: LucideIcon;
  onOpenChange: (open: boolean) => void;
}

/**
 * A record's detail page as the right sidebar: the same summary and tabs (details to edit, emails, reports,
 * connections) and a button that opens the full page. Mount it while open; closing calls `onOpenChange(false)`.
 */
export function RecordSheet({ collectionId, recordId, icon = Database, onOpenChange }: RecordSheetProps) {
  // Shared with RecordDetail inside (same query keys), so nothing is loaded twice.
  const { data: collection } = useGetCollection(collectionId);
  const { data: record } = useGetRecord(collectionId, recordId, { query: { retry: false } });
  const title = record ? (record.title ?? `Record #${record.id}`) : "Loading…";

  return (
    <DetailSheet
      open
      onOpenChange={onOpenChange}
      title={title}
      label={collection?.name}
      icon={icon}
      tint={collection ? collectionTintNumber(collection.id, collection.slug) : undefined}
      actions={
        // Same variant and size as the sheet's close button, so the two sit as a pair.
        <Button asChild variant="ghost" size="icon-sm">
          <Link href={recordHref(collectionId, recordId)} aria-label="Open full screen" title="Open full screen">
            <Maximize2 aria-hidden="true" />
          </Link>
        </Button>
      }
    >
      <RecordDetail collectionId={collectionId} recordId={recordId} variant="sheet" />
    </DetailSheet>
  );
}
