"use client";

import { Database, FolderPlus, SearchX } from "lucide-react";
import Link from "next/link";
import { useDeferredValue, useState } from "react";
import { EmptyState } from "@/components/app/EmptyState";
import { ErrorMessage } from "@/components/app/ErrorMessage";
import { FilterChip } from "@/components/app/FilterChip";
import { SearchField } from "@/components/app/SearchField";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { useListCollections } from "@/lib/api/generated/endpoints/collections/collections";
import { useListRecords } from "@/lib/api/generated/endpoints/records/records";
import type { CollectionDto } from "@/lib/api/generated/model/collectionDto";
import { ROW_HOVER } from "@/lib/card-hover";
import { collectionTintNumber, tintVividClass } from "@/lib/collection-tint";
import { collectionHref, NEW_COLLECTION_HREF } from "@/lib/navigation";
import { cn } from "@/lib/utils";

type Origin = "all" | "builtin" | "yours" | "ai";

const ORIGINS: readonly { value: Origin; label: string; matches: (collection: CollectionDto) => boolean }[] = [
  { value: "all", label: "All", matches: () => true },
  { value: "builtin", label: "Built-in", matches: (collection) => collection.system },
  { value: "yours", label: "Yours", matches: (collection) => !collection.system && collection.createdBy === "USER" },
  { value: "ai", label: "By the assistant", matches: (collection) => !collection.system && collection.createdBy === "AI" },
];

/** Only the total matters: one row is the smallest page. */
const COUNT_ONLY = { size: 1 } as const;
const SKELETON_WIDTHS = ["w-40", "w-32", "w-48", "w-36"] as const;

function originLabel(collection: CollectionDto): string {
  if (collection.system) return "Built-in";
  return collection.createdBy === "AI" ? "By the assistant" : "Yours";
}

function matchesText(collection: CollectionDto, text: string): boolean {
  const needle = text.trim().toLowerCase();
  return !needle || collection.name.toLowerCase().includes(needle) || (collection.description ?? "").toLowerCase().includes(needle);
}

/** Every collection in one searchable list: name and description, records, fields and who defined it. */
export function CollectionsOverview() {
  const { data: collections, isPending, error } = useListCollections();
  const [origin, setOrigin] = useState<Origin>("all");
  const [query, setQuery] = useState("");
  // Typing stays responsive while the list filters.
  const text = useDeferredValue(query);

  if (error) return <ErrorMessage message={error.message} />;
  if (isPending) return <LoadingRows />;
  if (collections.length === 0) {
    return (
      <EmptyState
        icon={Database}
        title="No collections yet"
        description="Collections hold your own data: deliveries, orders, people and more. The assistant suggests new ones from your mail."
        action={
          <Button asChild>
            <Link href={NEW_COLLECTION_HREF}>
              <FolderPlus aria-hidden="true" data-icon="inline-start" />
              New collection
            </Link>
          </Button>
        }
      />
    );
  }

  const sorted = collections.toSorted((a, b) => a.name.localeCompare(b.name));
  const filter = ORIGINS.find((entry) => entry.value === origin) ?? ORIGINS[0];
  const shown = sorted.filter((collection) => filter.matches(collection) && matchesText(collection, text));

  return (
    <section aria-label="Collections" className="grid gap-3">
      <div className="flex flex-wrap items-center gap-3">
        <SearchField
          wrapperClassName="w-full max-w-xs"
          aria-label="Search collections"
          placeholder="Search collections…"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          onClear={() => setQuery("")}
        />
        <div className="flex flex-wrap gap-1.5">
          {ORIGINS.map((entry) => (
            <FilterChip
              key={entry.value}
              label={entry.label}
              count={sorted.filter(entry.matches).length}
              active={origin === entry.value}
              onClick={() => setOrigin(entry.value)}
            />
          ))}
        </div>
        <span className="ml-auto text-xs text-muted-foreground tabular-nums">
          {shown.length} {shown.length === 1 ? "collection" : "collections"}
        </span>
      </div>

      {shown.length === 0 ? (
        <div className="flex flex-col items-center gap-2 py-10 text-center">
          <SearchX aria-hidden="true" className="size-5 text-muted-foreground" strokeWidth={1.5} />
          <p className="text-sm font-medium">No collection matches</p>
          <Button
            variant="outline"
            size="xs"
            onClick={() => {
              setQuery("");
              setOrigin("all");
            }}
          >
            Show all
          </Button>
        </div>
      ) : (
        <table className="w-full table-fixed text-sm">
          <thead>
            <tr className="border-b text-left text-xs text-muted-foreground">
              <th scope="col" className="py-2 pr-4 font-normal">Name</th>
              <th scope="col" className="w-24 py-2 pr-4 text-right font-normal">Records</th>
              <th scope="col" className="hidden w-20 py-2 pr-4 text-right font-normal md:table-cell">Fields</th>
              <th scope="col" className="hidden w-40 py-2 font-normal md:table-cell">Origin</th>
            </tr>
          </thead>
          <tbody>
            {shown.map((collection) => (
              <CollectionRow key={collection.id} collection={collection} />
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}

/** One collection; the whole row opens its table (the name is the link, stretched over the row). */
function CollectionRow({ collection }: { collection: CollectionDto }) {
  const fields = collection.fields.filter((field) => !field.archivedAt).length;
  return (
    <tr className={cn("relative border-b last:border-b-0 focus-within:bg-muted/50", ROW_HOVER)}>
      <td className="py-2.5 pr-4">
        <div className="flex min-w-0 items-start gap-3">
          <Database
            aria-hidden="true"
            strokeWidth={1.75}
            className={cn("mt-0.5 size-4 shrink-0", tintVividClass(collectionTintNumber(collection.id, collection.slug)))}
          />
          <div className="grid min-w-0 gap-0.5">
            <Link
              href={collectionHref(collection.id)}
              className="truncate font-medium outline-none after:absolute after:inset-0 after:content-[''] hover:underline focus-visible:underline"
            >
              {collection.name}
            </Link>
            {collection.description ? <span className="truncate text-xs text-muted-foreground">{collection.description}</span> : null}
          </div>
        </div>
      </td>
      <td className="py-2.5 pr-4 text-right tabular-nums">
        <RecordCount collectionId={collection.id} />
      </td>
      <td className="hidden py-2.5 pr-4 text-right tabular-nums md:table-cell">{fields}</td>
      <td className="hidden py-2.5 text-muted-foreground md:table-cell">{originLabel(collection)}</td>
    </tr>
  );
}

function RecordCount({ collectionId }: { collectionId: number }) {
  const { data } = useListRecords(collectionId, COUNT_ONLY);
  return data ? <>{data.totalElements}</> : <Skeleton className="ml-auto h-4 w-8" />;
}

function LoadingRows() {
  return (
    <div aria-label="Loading collections" className="grid gap-3 py-2">
      <Skeleton className="h-9 w-80" />
      {SKELETON_WIDTHS.map((width) => (
        <div key={width} className="flex items-center gap-3 border-b py-2.5">
          <Skeleton className="size-4" />
          <Skeleton className={cn("h-4", width)} />
          <Skeleton className="ml-auto h-4 w-8" />
        </div>
      ))}
    </div>
  );
}
