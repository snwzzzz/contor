"use client";

import { keepPreviousData } from "@tanstack/react-query";
import { ArrowDown, ArrowUp, ArrowUpDown, ArrowUpRight, ChevronLeft, ChevronRight, Columns3, Database, FolderPlus, ListFilter, Search, SearchX, Table2, X } from "lucide-react";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState, type KeyboardEvent } from "react";
import { AppButton } from "@/components/app/AppButton";
import { EmptyState } from "@/components/app/EmptyState";
import { ErrorMessage } from "@/components/app/ErrorMessage";
import { RefreshCommand } from "@/components/app/PageCommandButtons";
import { CommandLink, PageCommands } from "@/components/app/PageCommands";
import { COLLECTIONS_CRUMB, PageHeader } from "@/components/app/PageHeader";
import { CollectionReports } from "@/components/app/reports/CollectionReports";
import {
  DropdownMenu,
  DropdownMenuCheckboxItem,
  DropdownMenuContent,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Input } from "@/components/ui/input";
import { useGetCollection } from "@/lib/api/generated/endpoints/collections/collections";
import { useListRecords } from "@/lib/api/generated/endpoints/records/records";
import type { CollectionFieldDto } from "@/lib/api/generated/model/collectionFieldDto";
import type { RecordDto } from "@/lib/api/generated/model/recordDto";
import { collectionTintNumber, tintClass, type Tint } from "@/lib/collection-tint";
import { navItem, NEW_COLLECTION_HREF } from "@/lib/navigation";
import { pageItems } from "@/lib/pagination";
import { formatValue } from "@/lib/proposal-format";
import { buildFilters, countActiveFilters, filterKind, type ColumnFilter, type FilterState } from "@/lib/record-filters";
import { useStoredValue, writeStoredValue } from "@/lib/stored-value";
import { cn } from "@/lib/utils";

const PAGE_SIZES = [10, 25, 50, 100] as const;
const DEFAULT_PAGE_SIZE = 25;
const SEARCH_DELAY_MS = 300;
const TITLE_COLUMN = "title";
const NO_HIDDEN: readonly string[] = [];

interface Column {
  key: string;
  name: string;
  field?: CollectionFieldDto;
}

export function recordHref(collectionId: number, recordId: number): string {
  return `/collections/${collectionId}/records/${recordId}`;
}

function hiddenColumnsKey(collectionId: number): string {
  return `contor.table.${collectionId}.hiddenColumns`;
}

function parseHidden(raw: unknown): readonly string[] | undefined {
  return Array.isArray(raw) && raw.every((key) => typeof key === "string") ? raw : undefined;
}

/** Choice values (ENUM/STATUS) as soft pills; each allowed value keeps its tint. */
function choiceTint(field: CollectionFieldDto, value: string): string {
  const options = field.options[field.type === "STATUS" ? "states" : "values"];
  const index = Array.isArray(options) ? options.indexOf(value) : -1;
  return index < 0 ? "bg-muted text-foreground" : tintClass(((index % 10) + 1) as Tint);
}

/** A collection's records as a table: search, column filters and visibility, sorting, numbered paging; double click opens. */
export function CollectionTable({ collectionId }: { collectionId: number }) {
  const router = useRouter();
  const { data: collection, error: collectionError } = useGetCollection(collectionId);
  const hidden = useStoredValue(hiddenColumnsKey(collectionId), NO_HIDDEN, parseHidden);
  const [search, setSearch] = useState("");
  const [query, setQuery] = useState("");
  const [filters, setFilters] = useState<FilterState>({});
  const [filtersOpen, setFiltersOpen] = useState(false);
  const [sort, setSort] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState<number>(DEFAULT_PAGE_SIZE);
  const searchTimer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);

  useEffect(() => () => clearTimeout(searchTimer.current), []);

  const fields = (collection?.fields ?? []).filter((field) => !field.archivedAt).toSorted((a, b) => a.position - b.position);
  const apiFilters = buildFilters(fields, filters);
  const { data: records, error, isPending } = useListRecords(
    collectionId,
    { filter: apiFilters.length > 0 ? apiFilters : undefined, q: query || undefined, sort: sort ?? undefined, page, size: pageSize },
    { query: { enabled: collection !== undefined, placeholderData: keepPreviousData } },
  );

  if (collectionError) {
    return <ErrorMessage message={collectionError.message} />;
  }
  if (!collection) {
    return <p className="text-sm text-muted-foreground">Loading…</p>;
  }

  const allColumns: Column[] = [{ key: TITLE_COLUMN, name: "Title" }, ...fields.map((field) => ({ key: field.slug, name: field.name, field }))];
  const columns = allColumns.filter((column) => !hidden.includes(column.key));
  const activeFilters = countActiveFilters(filters);
  const total = records?.totalElements ?? 0;
  const totalPages = records?.totalPages ?? 0;

  function onSearch(value: string) {
    setSearch(value);
    clearTimeout(searchTimer.current);
    searchTimer.current = setTimeout(() => {
      setQuery(value.trim());
      setPage(0);
    }, SEARCH_DELAY_MS);
  }

  function clearSearchAndFilters() {
    clearTimeout(searchTimer.current);
    setSearch("");
    setQuery("");
    setFilters({});
    setPage(0);
  }

  function setFilter(slug: string, input: ColumnFilter) {
    setFilters((current) => ({ ...current, [slug]: { ...current[slug], ...input } }));
    setPage(0);
  }

  function toggleSort(key: string) {
    setSort((current) => (current === key ? `-${key}` : current === `-${key}` ? null : key));
    setPage(0);
  }

  function toggleColumn(key: string) {
    writeStoredValue(hiddenColumnsKey(collectionId), hidden.includes(key) ? hidden.filter((other) => other !== key) : [...hidden, key]);
  }

  function open(record: RecordDto) {
    router.push(recordHref(collectionId, record.id));
  }

  return (
    <>
      <PageHeader
        trail={[COLLECTIONS_CRUMB]}
        icon={Database}
        tint={collectionTintNumber(collection.id, collection.slug)}
        title={collection.name}
        subtitle="Collection"
        description={collection.description}
        actions={
          <PageCommands>
            <RefreshCommand />
            <CommandLink href="/relations" icon={navItem("/relations").icon} label="Relations" />
            <CommandLink href="/queries" icon={navItem("/queries").icon} label="Queries" />
            <CommandLink href={NEW_COLLECTION_HREF} icon={FolderPlus} label="New collection" />
          </PageCommands>
        }
      />

      <CollectionReports collectionId={collectionId} collectionName={collection.name} filters={apiFilters} search={query}
        matching={records?.totalElements} />

      <section className="grid min-h-0 gap-2">
        <div className="flex flex-wrap items-center gap-2">
          <div className="relative w-full max-w-xs">
            <Search className="absolute top-1/2 left-2.5 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden="true" />
            <Input
              type="search"
              aria-label={`Search ${collection.name}`}
              placeholder={`Search ${collection.name.toLowerCase()}…`}
              value={search}
              onChange={(event) => onSearch(event.target.value)}
              className="h-9 pl-8"
            />
          </div>
          <div className="ml-auto flex flex-wrap items-center gap-2">
            {activeFilters > 0 ? (
              <AppButton variant="ghost" size="sm" onClick={() => { setFilters({}); setPage(0); }}>
                <X aria-hidden="true" data-icon="inline-start" />
                Clear filters
              </AppButton>
            ) : null}
            <label className="flex h-9 items-center gap-1.5 rounded-md px-2.5 text-sm hover:bg-accent">
              <span className="text-muted-foreground">Show</span>
              <select
                aria-label="Rows per page"
                value={pageSize}
                onChange={(event) => { setPageSize(Number(event.target.value)); setPage(0); }}
                className="bg-transparent outline-none"
              >
                {PAGE_SIZES.map((size) => (
                  <option key={size} value={size}>{size} rows</option>
                ))}
              </select>
            </label>
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <AppButton variant="ghost" size="sm" className="h-9">
                  <Columns3 aria-hidden="true" data-icon="inline-start" />
                  Columns
                </AppButton>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end">
                <DropdownMenuLabel>Show columns</DropdownMenuLabel>
                <DropdownMenuSeparator />
                {allColumns.filter((column) => column.key !== TITLE_COLUMN).map((column) => (
                  <DropdownMenuCheckboxItem
                    key={column.key}
                    checked={!hidden.includes(column.key)}
                    onCheckedChange={() => toggleColumn(column.key)}
                    onSelect={(event) => event.preventDefault()}
                  >
                    {column.name}
                  </DropdownMenuCheckboxItem>
                ))}
              </DropdownMenuContent>
            </DropdownMenu>
            <AppButton
              variant={filtersOpen ? "secondary" : "ghost"}
              size="sm"
              className="h-9"
              aria-label="Filters"
              aria-expanded={filtersOpen}
              onClick={() => setFiltersOpen((open) => !open)}
            >
              <ListFilter aria-hidden="true" />
              {activeFilters > 0 ? <span className="rounded-full bg-primary px-1.5 text-xs text-primary-foreground tabular-nums">{activeFilters}</span> : null}
            </AppButton>
          </div>
        </div>

        {error ? <ErrorMessage message={error.message} /> : null}

        <div className="min-h-0 overflow-auto">
          <table aria-label={collection.name} className="w-full border-collapse text-sm">
            <thead className="sticky top-0 z-10 bg-background text-left">
              <tr>
                {columns.map((column) => {
                  const direction = sort === column.key ? "ascending" : sort === `-${column.key}` ? "descending" : "none";
                  const SortIcon = direction === "ascending" ? ArrowUp : direction === "descending" ? ArrowDown : ArrowUpDown;
                  return (
                    <th key={column.key} scope="col" aria-sort={direction} className="border-b px-4 py-3 whitespace-nowrap">
                      <button
                        type="button"
                        aria-label={`Sort by ${column.name}`}
                        onClick={() => toggleSort(column.key)}
                        className="flex w-full items-center justify-between gap-3 rounded text-xs font-medium tracking-wide text-muted-foreground uppercase hover:text-foreground focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none"
                      >
                        {column.name}
                        <SortIcon className={cn("size-3.5 shrink-0", direction === "none" && "opacity-50")} aria-hidden="true" />
                      </button>
                    </th>
                  );
                })}
                <th scope="col" aria-label="Actions" className="w-12 border-b px-2 py-3" />
              </tr>
              {filtersOpen ? (
                <tr>
                  {columns.map((column) => (
                    <td key={column.key} className="border-b px-3 py-2 align-top">
                      {column.field ? (
                        <ColumnFilterInput field={column.field} value={filters[column.key] ?? {}} onChange={(input) => setFilter(column.key, input)} />
                      ) : null}
                    </td>
                  ))}
                  <td className="border-b" />
                </tr>
              ) : null}
            </thead>
            <tbody>
              {(records?.items ?? []).map((record) => (
                <tr
                  key={record.id}
                  tabIndex={0}
                  title="Double-click to open"
                  onDoubleClick={() => open(record)}
                  onKeyDown={(event: KeyboardEvent<HTMLTableRowElement>) => {
                    if (event.key === "Enter" && event.target === event.currentTarget) open(record);
                  }}
                  className="group cursor-pointer border-b last:border-b-0 hover:bg-muted/40 focus-visible:bg-muted/40 focus-visible:outline-none"
                >
                  {columns.map((column) => (
                    <td key={column.key} className={cn("max-w-72 truncate px-4 py-3.5", column.key === TITLE_COLUMN && "font-medium")}>
                      <CellValue value={column.field ? record.values[column.key] : record.title} field={column.field} />
                    </td>
                  ))}
                  <td className="px-2 py-2 text-right">
                    <AppButton
                      variant="ghost"
                      size="icon"
                      className="size-8 text-muted-foreground opacity-60 group-hover:opacity-100"
                      aria-label={`Open ${record.title ?? `record ${record.id}`}`}
                      onClick={() => open(record)}
                    >
                      <ArrowUpRight aria-hidden="true" />
                    </AppButton>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {!isPending && records?.items.length === 0 ? (
            query || activeFilters > 0 ? (
              <EmptyState
                icon={SearchX}
                title="No matching records"
                description={query ? `Nothing in ${collection.name} matches “${query}”${activeFilters > 0 ? " with these filters" : ""}.` : "Nothing matches these filters."}
                action={
                  <AppButton variant="outline" onClick={clearSearchAndFilters}>
                    <X aria-hidden="true" data-icon="inline-start" />
                    Clear search and filters
                  </AppButton>
                }
              />
            ) : (
              <EmptyState
                icon={Table2}
                title="No records"
                description={`The assistant adds ${collection.name.toLowerCase()} here as it finds them in your mail.`}
              />
            )
          ) : null}
        </div>

        {total > 0 ? <Pager total={total} page={page} totalPages={totalPages} onPage={setPage} /> : null}
      </section>
    </>
  );
}

function Pager({ total, page, totalPages, onPage }: { total: number; page: number; totalPages: number; onPage: (page: number) => void }) {
  const [goTo, setGoTo] = useState("");
  return (
    <div className="grid grid-cols-[1fr_auto_1fr] items-center gap-3 px-1 pt-2 text-sm">
      <span className="text-muted-foreground">{total === 1 ? "1 record" : `${total} records`}</span>
      {totalPages > 1 ? (
        <nav aria-label="Pages" className="flex items-center gap-1">
          {pageItems(page, totalPages).map((item, index) =>
            item === "gap" ? (
              <span key={`gap-${index}`} className="px-1 text-muted-foreground" aria-hidden="true">…</span>
            ) : (
              <button
                key={item}
                type="button"
                aria-label={`Page ${item}`}
                aria-current={item === page + 1 ? "page" : undefined}
                onClick={() => onPage(item - 1)}
                className={cn(
                  "flex size-8 items-center justify-center rounded-md border text-xs tabular-nums hover:bg-accent",
                  item === page + 1 && "border-primary bg-primary text-primary-foreground hover:bg-primary/90",
                )}
              >
                {item}
              </button>
            ),
          )}
          <AppButton variant="outline" size="icon" className="size-8" aria-label="Previous page" disabled={page === 0} onClick={() => onPage(page - 1)}>
            <ChevronLeft aria-hidden="true" />
          </AppButton>
          <AppButton variant="outline" size="icon" className="size-8" aria-label="Next page" disabled={page + 1 >= totalPages} onClick={() => onPage(page + 1)}>
            <ChevronRight aria-hidden="true" />
          </AppButton>
        </nav>
      ) : (
        <span />
      )}
      {totalPages > 1 ? (
        <label className="flex items-center justify-end gap-2 text-muted-foreground">
          Go to page
          <input
            type="number"
            min={1}
            max={totalPages}
            value={goTo}
            onChange={(event) => setGoTo(event.target.value)}
            onKeyDown={(event) => {
              if (event.key !== "Enter") return;
              const target = Number(goTo);
              if (Number.isInteger(target)) onPage(Math.min(totalPages, Math.max(1, target)) - 1);
              setGoTo("");
            }}
            className="h-8 w-14 rounded-md border bg-background px-2 text-center text-foreground"
          />
        </label>
      ) : (
        <span />
      )}
    </div>
  );
}

function CellValue({ value, field }: { value: unknown; field?: CollectionFieldDto }) {
  if (value === null || value === undefined || value === "") {
    return <span className="text-muted-foreground">–</span>;
  }
  if (field && (field.type === "ENUM" || field.type === "STATUS") && typeof value === "string") {
    return <span className={cn("rounded-md px-2 py-0.5 text-xs font-medium", choiceTint(field, value))}>{value}</span>;
  }
  return <>{formatValue(value)}</>;
}

const FILTER_INPUT = "h-8 w-full min-w-28 rounded-md border bg-background px-2 text-xs font-normal";

function ColumnFilterInput({ field, value, onChange }: { field: CollectionFieldDto; value: ColumnFilter; onChange: (input: ColumnFilter) => void }) {
  const kind = filterKind(field);
  const label = `Filter ${field.name}`;
  switch (kind) {
    case "text":
      return <input aria-label={label} placeholder="contains…" className={FILTER_INPUT} value={value.text ?? ""} onChange={(event) => onChange({ text: event.target.value })} />;
    case "choice": {
      const options = field.options[field.type === "STATUS" ? "states" : "values"];
      return (
        <select aria-label={label} className={FILTER_INPUT} value={value.equals ?? ""} onChange={(event) => onChange({ equals: event.target.value })}>
          <option value="">Any</option>
          {(Array.isArray(options) ? options : []).map((option) => (
            <option key={String(option)} value={String(option)}>
              {String(option)}
            </option>
          ))}
        </select>
      );
    }
    case "boolean":
      return (
        <select aria-label={label} className={FILTER_INPUT} value={value.equals ?? ""} onChange={(event) => onChange({ equals: event.target.value })}>
          <option value="">Any</option>
          <option value="true">Yes</option>
          <option value="false">No</option>
        </select>
      );
    case "number":
    case "date":
    case "datetime": {
      const type = kind === "number" ? "number" : "date";
      return (
        <div className="grid gap-1">
          <input type={type} aria-label={`${field.name} from`} placeholder="from" className={FILTER_INPUT} value={value.min ?? ""} onChange={(event) => onChange({ min: event.target.value })} />
          <input type={type} aria-label={`${field.name} to`} placeholder="to" className={FILTER_INPUT} value={value.max ?? ""} onChange={(event) => onChange({ max: event.target.value })} />
        </div>
      );
    }
    default:
      return null;
  }
}
