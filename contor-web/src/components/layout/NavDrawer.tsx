"use client";

import { Database, GripVertical, Menu, Plus } from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState, type DragEvent, type KeyboardEvent, type ReactNode } from "react";
import { SearchField } from "@/components/app/SearchField";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Sheet, SheetContent, SheetDescription, SheetTitle, SheetTrigger } from "@/components/ui/sheet";
import { Skeleton } from "@/components/ui/skeleton";
import type { CollectionDto } from "@/lib/api/generated/model/collectionDto";
import { useListCollections } from "@/lib/api/generated/endpoints/collections/collections";
import { useCountProposals } from "@/lib/api/generated/endpoints/proposals/proposals";
import { moveItem, orderByIds } from "@/lib/collection-order";
import { collectionTintNumber, tintVividClass } from "@/lib/collection-tint";
import {
  AI_INSIGHTS_NAV,
  collectionHref,
  DATA_NAV,
  HOME_HREF,
  isActive,
  NEW_COLLECTION_HREF,
  PRIMARY_NAV,
  type NavItem,
} from "@/lib/navigation";
import { useStoredValue, writeStoredValue } from "@/lib/stored-value";
import { cn } from "@/lib/utils";

const PROPOSAL_COUNT_REFRESH_MS = 60_000;
const COLLECTION_ORDER_KEY = "contor.sidebar.collectionOrder";
const NO_ORDER: readonly number[] = [];
/** Compact rows (shadcn sidebar style): inset and rounded; color only on the icon; the current page gets the accent background. */
const ROW = "mx-2 flex h-8 items-center gap-2.5 rounded-md px-2 text-sm outline-none transition-colors hover:bg-accent focus-visible:bg-accent";
const ACTIVE_ROW = "bg-accent font-medium text-accent-foreground";

function parseOrder(raw: unknown): readonly number[] | undefined {
  return Array.isArray(raw) && raw.every((id) => typeof id === "number") ? raw : undefined;
}

function saveOrder(collections: readonly CollectionDto[]) {
  writeStoredValue(COLLECTION_ORDER_KEY, collections.map((collection) => collection.id));
}

/** Every path starts with "/": home is current only on itself. */
function isCurrent(pathname: string, href: string): boolean {
  return href === HOME_HREF ? pathname === HOME_HREF : isActive(pathname, href);
}

function matches(query: string, title: string): boolean {
  return title.toLowerCase().includes(query);
}

/** Hamburger in the top bar; opens the navigation as a panel over the page (closes on outside click, Escape, Ctrl/⌘ B). */
export function NavDrawer() {
  const [open, setOpen] = useState(false);

  useEffect(() => {
    function onKeyDown(event: globalThis.KeyboardEvent) {
      if (event.key.toLowerCase() === "b" && (event.ctrlKey || event.metaKey) && !event.altKey && !event.shiftKey) {
        event.preventDefault();
        setOpen((current) => !current);
      }
    }
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, []);

  return (
    <Sheet open={open} onOpenChange={setOpen}>
      <SheetTrigger asChild>
        <Button variant="ghost" size="icon" aria-label="Menu">
          <Menu aria-hidden="true" />
        </Button>
      </SheetTrigger>
      <SheetContent
        side="left"
        showCloseButton={false}
        overlayClassName="bg-transparent supports-backdrop-filter:backdrop-blur-none"
        // Back to the menu button only when nothing else took the focus while it closed (e.g. Ctrl+K into the search bar).
        onCloseAutoFocus={(event) => {
          if (document.activeElement && document.activeElement !== document.body) event.preventDefault();
        }}
        // Starts below the top bar, like a flyout.
        className="gap-0 p-0 data-[side=left]:top-(--header-height) data-[side=left]:h-[calc(100svh-var(--header-height))] data-[side=left]:w-72 data-[side=left]:sm:max-w-72"
      >
        <SheetTitle className="sr-only">Navigation</SheetTitle>
        <SheetDescription className="sr-only">Go to a page or collection</SheetDescription>
        <NavDrawerContent onNavigate={() => setOpen(false)} />
      </SheetContent>
    </Sheet>
  );
}

/** Mounted only while the drawer is open, so collections and counts are fetched when needed. */
function NavDrawerContent({ onNavigate }: { onNavigate: () => void }) {
  const pathname = usePathname();
  const [query, setQuery] = useState("");
  const search = query.trim().toLowerCase();
  const { data: actions } = useCountProposals({ group: "ACTIONS" }, { query: { refetchInterval: PROPOSAL_COUNT_REFRESH_MS } });
  const { data: findings } = useCountProposals({ group: "FINDINGS" }, { query: { refetchInterval: PROPOSAL_COUNT_REFRESH_MS } });
  const { data: collections, isPending } = useListCollections();
  const badges: Record<string, { count: number | undefined; label: string }> = {
    "/proposals": { count: actions?.count, label: "pending proposals" },
    "/findings": { count: findings?.count, label: "new findings" },
  };

  const primary = PRIMARY_NAV.filter((item) => matches(search, item.title));
  const insights = AI_INSIGHTS_NAV.filter((item) => matches(search, item.title));
  const data = DATA_NAV.filter((item) => matches(search, item.title));
  const foundCollections = search ? (collections ?? []).filter((collection) => matches(search, collection.name)) : null;
  const showNewCollection = matches(search, "New collection");
  const nothingFound =
    search !== "" && primary.length + insights.length + data.length + (foundCollections?.length ?? 0) === 0 && !showNewCollection;

  function navLinks(items: readonly NavItem[]) {
    return items.map((item) => (
      <NavRow key={item.href} item={item} pathname={pathname} badge={badges[item.href]} onNavigate={onNavigate} />
    ));
  }

  return (
    <>
      <div className="shrink-0 border-b p-2">
        <SearchField
          aria-label="Search pages"
          placeholder="Search pages…"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          onClear={() => setQuery("")}
        />
      </div>
      <nav aria-label="Main" className="min-h-0 flex-1 overflow-y-auto pb-2">
        {primary.length > 0 ? <NavGroup>{navLinks(primary)}</NavGroup> : null}
        {insights.length > 0 ? <NavGroup label="AI insights">{navLinks(insights)}</NavGroup> : null}
        {foundCollections === null ? (
          <CollectionsGroup collections={collections} isPending={isPending} pathname={pathname} onNavigate={onNavigate} />
        ) : foundCollections.length > 0 || showNewCollection ? (
          <NavGroup label="Collections">
            {foundCollections.map((collection) => (
              <li key={collection.id}>
                <CollectionLink collection={collection} pathname={pathname} onNavigate={onNavigate} />
              </li>
            ))}
            {showNewCollection ? <NewCollectionRow onNavigate={onNavigate} /> : null}
          </NavGroup>
        ) : null}
        {data.length > 0 ? <NavGroup label="Graph & automation">{navLinks(data)}</NavGroup> : null}
        {nothingFound ? <p className="px-4 py-6 text-center text-sm text-muted-foreground">No pages found</p> : null}
      </nav>
    </>
  );
}

function NavGroup({ label, children }: { label?: string; children: ReactNode }) {
  const id = label ? `nav-${label.toLowerCase().replaceAll(/[^a-z]+/g, "-")}` : undefined;
  return (
    <div className="pt-2">
      {label ? (
        <h3 id={id} className="px-4 pt-2 pb-1 text-xs text-muted-foreground">
          {label}
        </h3>
      ) : null}
      <ul aria-labelledby={id} className="grid gap-px">
        {children}
      </ul>
    </div>
  );
}

interface NavRowProps {
  item: NavItem;
  pathname: string;
  badge?: { count: number | undefined; label: string };
  onNavigate: () => void;
}

function NavRow({ item, pathname, badge, onNavigate }: NavRowProps) {
  const active = isCurrent(pathname, item.href);
  return (
    <li>
      <Link href={item.href} onClick={onNavigate} aria-current={active ? "page" : undefined} className={cn(ROW, active && ACTIVE_ROW)}>
        <item.icon aria-hidden="true" className={cn("size-4 shrink-0", item.tint ? tintVividClass(item.tint) : "text-muted-foreground")} />
        <span className="truncate">{item.title}</span>
        {badge?.count ? (
          <Badge variant="secondary" className="ml-auto" aria-label={`${badge.count} ${badge.label}`}>
            {badge.count}
          </Badge>
        ) : null}
      </Link>
    </li>
  );
}

function CollectionLink({ collection, pathname, onNavigate }: { collection: CollectionDto; pathname: string; onNavigate: () => void }) {
  const href = collectionHref(collection.id);
  const active = isActive(pathname, href);
  return (
    <Link href={href} onClick={onNavigate} draggable={false} aria-current={active ? "page" : undefined} className={cn(ROW, "pr-8", active && ACTIVE_ROW)}>
      <Database aria-hidden="true" className={cn("size-4 shrink-0", tintVividClass(collectionTintNumber(collection.id, collection.slug)))} />
      <span className="truncate">{collection.name}</span>
    </Link>
  );
}

function NewCollectionRow({ onNavigate }: { onNavigate: () => void }) {
  return (
    <li>
      <Link href={NEW_COLLECTION_HREF} onClick={onNavigate} className={cn(ROW, "text-muted-foreground")}>
        <Plus aria-hidden="true" className="size-4 shrink-0" />
        <span>New collection</span>
      </Link>
    </li>
  );
}

interface CollectionsGroupProps {
  collections: CollectionDto[] | undefined;
  isPending: boolean;
  pathname: string;
  onNavigate: () => void;
}

/** The collections in the saved order; reorder by dragging or with Alt+Up/Down on a grip. */
function CollectionsGroup({ collections, isPending, pathname, onNavigate }: CollectionsGroupProps) {
  const savedOrder = useStoredValue(COLLECTION_ORDER_KEY, NO_ORDER, parseOrder);
  /** While dragging: where the item came from and where it would land (indexes in the saved order). */
  const [drag, setDrag] = useState<{ from: number; to: number } | null>(null);
  const ordered = orderByIds(collections ?? [], savedOrder);
  // Live preview: the list is shown as if the dragged item were already at the hovered position.
  const shown = drag ? moveItem(ordered, drag.from, drag.to) : ordered;

  function move(from: number, to: number) {
    saveOrder(moveItem(ordered, from, to));
  }

  return (
    <NavGroup label="Collections">
      {isPending ? (
        <li className="px-4 py-2">
          <Skeleton className="h-5" />
        </li>
      ) : null}
      {shown.map((collection, index) => (
        <CollectionItem
          key={collection.id}
          collection={collection}
          pathname={pathname}
          onNavigate={onNavigate}
          preview={drag !== null && index === drag.to}
          onDragStart={() => setDrag({ from: index, to: index })}
          onDragEnd={() => setDrag(null)}
          onDragOver={(event) => {
            event.preventDefault();
            setDrag((current) => (current && current.to !== index ? { ...current, to: index } : current));
          }}
          onDrop={(event) => {
            event.preventDefault();
            if (drag) move(drag.from, drag.to);
            setDrag(null);
          }}
          onMove={(offset) => move(index, index + offset)}
        />
      ))}
      <NewCollectionRow onNavigate={onNavigate} />
    </NavGroup>
  );
}

interface CollectionItemProps {
  collection: CollectionDto;
  pathname: string;
  onNavigate: () => void;
  /** This is the dragged item, shown at the position it would be dropped at. */
  preview: boolean;
  onDragStart: () => void;
  onDragEnd: () => void;
  onDragOver: (event: DragEvent<HTMLLIElement>) => void;
  onDrop: (event: DragEvent<HTMLLIElement>) => void;
  /** -1 = up, +1 = down (keyboard). */
  onMove: (offset: number) => void;
}

function CollectionItem({ collection, pathname, onNavigate, preview, onDragStart, onDragEnd, onDragOver, onDrop, onMove }: CollectionItemProps) {
  function onGripKeyDown(event: KeyboardEvent<HTMLButtonElement>) {
    if (!event.altKey || (event.key !== "ArrowUp" && event.key !== "ArrowDown")) return;
    event.preventDefault();
    onMove(event.key === "ArrowUp" ? -1 : 1);
  }

  return (
    <li
      draggable
      onDragStart={(event) => {
        event.dataTransfer.effectAllowed = "move";
        onDragStart();
      }}
      onDragEnd={onDragEnd}
      onDragOver={onDragOver}
      onDrop={onDrop}
      data-drag-preview={preview || undefined}
      className={cn("group/collection relative border border-transparent", preview && "border-dashed border-ring bg-accent/60 opacity-70")}
    >
      <CollectionLink collection={collection} pathname={pathname} onNavigate={onNavigate} />
      <button
        type="button"
        aria-label={`Move ${collection.name}`}
        title="Drag, or Alt+Up/Down to move"
        onKeyDown={onGripKeyDown}
        className="absolute top-1.5 right-3 flex size-5 cursor-grab items-center justify-center rounded text-muted-foreground opacity-0 outline-none group-hover/collection:opacity-100 hover:bg-accent focus-visible:opacity-100 focus-visible:ring-2 focus-visible:ring-ring"
      >
        <GripVertical className="size-3.5" aria-hidden="true" />
      </button>
    </li>
  );
}
