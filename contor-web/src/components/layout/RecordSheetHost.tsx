"use client";

import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { RecordSheet } from "@/components/app/collections/RecordSheet";
import {
  closeRecordSheetHref,
  parseRecordParam,
  parseRecordTab,
  RECORD_PARAM,
  RECORD_TAB_PARAM,
  recordSheetTabHref,
} from "@/lib/record-sheet";

const HYDRATION_POLL_MS = 100;
/** Opens anyway after this long (e.g. when something outside React added nodes to the page). */
const HYDRATION_WAIT_MS = 10_000;

/** React marks every DOM node it has hydrated with its fiber; server HTML still waiting for its code has none. */
function pageHydrated(): boolean {
  const main = document.querySelector("main");
  if (!main) return true;
  return Array.from(main.querySelectorAll("*")).every((node) => Object.keys(node).some((key) => key.startsWith("__reactFiber$")));
}

/**
 * True once React has hydrated the page under the sidebar. The sidebar shares queries with the page (the collection,
 * the record); opened earlier, e.g. from a reload with `?record=`, its data could reach a part of the page that is still
 * hydrating, which would then render something else than the server's HTML.
 */
function usePageHydrated(): boolean {
  const [hydrated, setHydrated] = useState(false);
  useEffect(() => {
    const started = Date.now();
    const check = () => {
      if (!pageHydrated() && Date.now() - started < HYDRATION_WAIT_MS) return false;
      setHydrated(true);
      return true;
    };
    if (check()) return;
    const timer = window.setInterval(() => {
      if (check()) window.clearInterval(timer);
    }, HYDRATION_POLL_MS);
    return () => window.clearInterval(timer);
  }, []);
  return hydrated;
}

/**
 * The record detail sidebar over any page, opened by the address (`?record=3-12&recordTab=emails`, built by
 * `recordSheetHref`): links, reloads and Back all work, and the tab survives a reload. Opening adds one history entry;
 * switching tabs or records replaces it. Closing goes Back when the sidebar was opened from this very page (so the page is
 * never in the history twice), and otherwise (opened from a link or a reload) replaces the address with the page's.
 */
export function RecordSheetHost() {
  const pathname = usePathname();
  const router = useRouter();
  const searchParams = useSearchParams();
  const target = parseRecordParam(searchParams.get(RECORD_PARAM));
  const search = searchParams.toString();
  const here = search ? `${pathname}?${search}` : pathname;
  // The address of the page while no record was open in it.
  const pageHref = useRef<string | null>(null);

  const open = target !== null;
  const hydrated = usePageHydrated();

  useEffect(() => {
    if (!open) pageHref.current = here;
  }, [open, here]);

  if (!target || !hydrated) return null;

  function close() {
    const href = closeRecordSheetHref(pathname, searchParams);
    if (pageHref.current === href) router.back();
    else router.replace(href, { scroll: false });
  }

  return (
    <RecordSheet
      key={`${target.collectionId}-${target.recordId}`}
      collectionId={target.collectionId}
      recordId={target.recordId}
      tab={parseRecordTab(searchParams.get(RECORD_TAB_PARAM))}
      onTabChange={(tab) => router.replace(recordSheetTabHref(pathname, searchParams, tab), { scroll: false })}
      onOpenChange={(open) => {
        if (!open) close();
      }}
    />
  );
}
