/**
 * Where a record is shown: its own page (`/collections/3/records/12?tab=emails`) or the detail sidebar over whatever
 * page is open (`?record=3-12&recordTab=emails`). Both live in the URL, so links, reloads and Back work for either.
 */
export type RecordTab = "details" | "emails" | "reports" | "connections";

const RECORD_TABS: readonly RecordTab[] = ["details", "emails", "reports", "connections"];
const DEFAULT_TAB: RecordTab = "details";

/** The record shown in the sidebar, `<collectionId>-<recordId>` (record IDs are unique per collection only). */
export const RECORD_PARAM = "record";
/** The sidebar's tab; a separate name from the page's `tab`, so a sidebar over a record page leaves the page's tab alone. */
export const RECORD_TAB_PARAM = "recordTab";
/** The tab of a record's own page. */
export const PAGE_TAB_PARAM = "tab";

const RECORD_PATTERN = /^([1-9]\d*)-([1-9]\d*)$/;

export function parseRecordParam(raw: string | null): { collectionId: number; recordId: number } | null {
  const match = raw ? RECORD_PATTERN.exec(raw) : null;
  return match ? { collectionId: Number(match[1]), recordId: Number(match[2]) } : null;
}

export function parseRecordTab(raw: string | null): RecordTab {
  return RECORD_TABS.find((tab) => tab === raw) ?? DEFAULT_TAB;
}

function withParams(pathname: string, params: URLSearchParams): string {
  const query = params.toString();
  return query ? `${pathname}?${query}` : pathname;
}

/** A record's own page, at `tab` (Details needs no parameter). */
export function recordHref(collectionId: number, recordId: number, tab: RecordTab = DEFAULT_TAB): string {
  const page = `/collections/${collectionId}/records/${recordId}`;
  return tab === DEFAULT_TAB ? page : `${page}?${PAGE_TAB_PARAM}=${tab}`;
}

/** The current page with the record open in the sidebar, on its Details tab; the page's own parameters stay. */
export function recordSheetHref(pathname: string, params: URLSearchParams, collectionId: number, recordId: number): string {
  const next = new URLSearchParams(params);
  next.set(RECORD_PARAM, `${collectionId}-${recordId}`);
  next.delete(RECORD_TAB_PARAM);
  return withParams(pathname, next);
}

/** The current page with the sidebar on another tab. */
export function recordSheetTabHref(pathname: string, params: URLSearchParams, tab: RecordTab): string {
  const next = new URLSearchParams(params);
  if (tab === DEFAULT_TAB) next.delete(RECORD_TAB_PARAM);
  else next.set(RECORD_TAB_PARAM, tab);
  return withParams(pathname, next);
}

/** The current page without the sidebar. */
export function closeRecordSheetHref(pathname: string, params: URLSearchParams): string {
  const next = new URLSearchParams(params);
  next.delete(RECORD_PARAM);
  next.delete(RECORD_TAB_PARAM);
  return withParams(pathname, next);
}
