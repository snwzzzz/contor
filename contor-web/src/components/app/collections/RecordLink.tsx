"use client";

import Link from "next/link";
import { usePathname, useSearchParams } from "next/navigation";
import type { ComponentProps } from "react";
import { RECORD_PARAM, recordSheetHref } from "@/lib/record-sheet";

type RecordLinkProps = Omit<ComponentProps<typeof Link>, "href" | "scroll" | "replace"> & { collectionId: number; recordId: number };

/**
 * A link that opens a record in the detail sidebar over the current page (`RecordSheetHost`). It is a real address, so
 * Ctrl/⌘-click, the middle button and "copy link" open the same page with the sidebar; the page does not scroll. With the
 * sidebar already open it switches records in place, so the sidebar stays one history entry and Back closes it.
 */
export function RecordLink({ collectionId, recordId, ...props }: RecordLinkProps) {
  const pathname = usePathname();
  const searchParams = useSearchParams();
  return (
    <Link
      {...props}
      scroll={false}
      replace={searchParams.has(RECORD_PARAM)}
      href={recordSheetHref(pathname, searchParams, collectionId, recordId)}
    />
  );
}
