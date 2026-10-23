"use client";

import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { PAGE_TAB_PARAM, parseRecordTab, type RecordTab } from "@/lib/record-sheet";
import { RecordDetail } from "./RecordDetail";

/** A record's own page, its tab in the address (`?tab=emails`; Details without one), e.g. from the sidebar's full screen. */
export function RecordPage({ collectionId, recordId }: { collectionId: number; recordId: number }) {
  const pathname = usePathname();
  const router = useRouter();
  const searchParams = useSearchParams();

  function changeTab(tab: RecordTab) {
    const next = new URLSearchParams(searchParams);
    if (tab === "details") next.delete(PAGE_TAB_PARAM);
    else next.set(PAGE_TAB_PARAM, tab);
    const query = next.toString();
    router.replace(query ? `${pathname}?${query}` : pathname, { scroll: false });
  }

  return (
    <RecordDetail
      collectionId={collectionId}
      recordId={recordId}
      tab={parseRecordTab(searchParams.get(PAGE_TAB_PARAM))}
      onTabChange={changeTab}
    />
  );
}
