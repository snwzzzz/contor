"use client";

import { Database, GripVertical, Plus } from "lucide-react";
import Link from "next/link";
import { usePathname, useSearchParams } from "next/navigation";
import { useState, type DragEvent, type KeyboardEvent } from "react";
import { Badge } from "@/components/ui/badge";
import {
  Sidebar,
  SidebarContent,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarMenuSkeleton,
  SidebarMenuSub,
  SidebarMenuSubButton,
  SidebarMenuSubItem,
  SidebarRail,
  useSidebar,
} from "@/components/ui/sidebar";
import type { CollectionDto } from "@/lib/api/generated/model/collectionDto";
import { moveItem, orderByIds } from "@/lib/collection-order";
import { collectionTint, tintVividClass } from "@/lib/collection-tint";
import { useStoredValue, writeStoredValue } from "@/lib/stored-value";
import { cn } from "@/lib/utils";
import { useListCollections } from "@/lib/api/generated/endpoints/collections/collections";
import { useCountProposals } from "@/lib/api/generated/endpoints/proposals/proposals";
import {
  collectionHref,
  DATA_NAV,
  isActive,
  NEW_COLLECTION_HREF,
  PRIMARY_NAV,
  isSettingsSection,
  SETTINGS_NAV,
  SETTINGS_PARAM,
  SETTINGS_ROOT,
  settingsHref,
  type NavItem,
} from "@/lib/navigation";
import { SidebarResizeHandle } from "./SidebarResizeHandle";

const PROPOSAL_COUNT_REFRESH_MS = 60_000;
const COLLECTION_ORDER_KEY = "contor.sidebar.collectionOrder";
const NO_ORDER: readonly number[] = [];
function parseOrder(raw: unknown): readonly number[] | undefined {
  return Array.isArray(raw) && raw.every((id) => typeof id === "number") ? raw : undefined;
}

function saveOrder(collections: readonly CollectionDto[]) {
  writeStoredValue(COLLECTION_ORDER_KEY, collections.map((collection) => collection.id));
}

function NavLink({ item, pathname, badge, badgeLabel }: { item: NavItem; pathname: string; badge?: number; badgeLabel?: string }) {
  const active = isActive(pathname, item.href);
  return (
    <SidebarMenuItem>
      <SidebarMenuButton asChild isActive={active} tooltip={item.title}>
        <Link href={item.href} aria-current={active ? "page" : undefined}>
          <item.icon aria-hidden="true" className={item.tint ? tintVividClass(item.tint) : undefined} />
          <span>{item.title}</span>
          {badge ? (
            <Badge variant="secondary" className="ml-auto" aria-label={`${badge} ${badgeLabel ?? "pending"}`}>
              {badge}
            </Badge>
          ) : null}
        </Link>
      </SidebarMenuButton>
    </SidebarMenuItem>
  );
}

function CollectionsGroup({ pathname }: { pathname: string }) {
  const { data, isPending } = useListCollections();
  const savedOrder = useStoredValue(COLLECTION_ORDER_KEY, NO_ORDER, parseOrder);
  /** While dragging: where the item came from and where it would land (indexes in the saved order). */
  const [drag, setDrag] = useState<{ from: number; to: number } | null>(null);
  const collections = orderByIds(data ?? [], savedOrder);
  // Live preview: the list is shown as if the dragged item were already at the hovered position.
  const shown = drag ? moveItem(collections, drag.from, drag.to) : collections;

  function move(from: number, to: number) {
    saveOrder(moveItem(collections, from, to));
  }

  return (
    <SidebarGroup>
      <SidebarGroupLabel id="collections-label">Collections</SidebarGroupLabel>
      <SidebarGroupContent>
        <SidebarMenu aria-labelledby="collections-label">
          {isPending ? (
            <SidebarMenuItem>
              <SidebarMenuSkeleton showIcon />
            </SidebarMenuItem>
          ) : null}
          {shown.map((collection, index) => (
            <CollectionItem
              key={collection.id}
              collection={collection}
              pathname={pathname}
              tint={collectionTint(collection.id, collection.slug)}
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
          <SidebarMenuItem>
            <SidebarMenuButton asChild tooltip="New collection">
              <Link href={NEW_COLLECTION_HREF}>
                <Plus aria-hidden="true" />
                <span>New collection</span>
              </Link>
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarGroupContent>
    </SidebarGroup>
  );
}

interface CollectionItemProps {
  collection: CollectionDto;
  pathname: string;
  tint: string;
  /** This is the dragged item, shown at the position it would be dropped at. */
  preview: boolean;
  onDragStart: () => void;
  onDragEnd: () => void;
  onDragOver: (event: DragEvent<HTMLLIElement>) => void;
  onDrop: (event: DragEvent<HTMLLIElement>) => void;
  /** -1 = up, +1 = down (keyboard). */
  onMove: (offset: number) => void;
}

/** A collection link that can be dragged to a new position, or moved with Alt+Up/Down on its grip. */
function CollectionItem({ collection, pathname, tint, preview, onDragStart, onDragEnd, onDragOver, onDrop, onMove }: CollectionItemProps) {
  const href = collectionHref(collection.id);
  const active = isActive(pathname, href);

  function onGripKeyDown(event: KeyboardEvent<HTMLButtonElement>) {
    if (!event.altKey || (event.key !== "ArrowUp" && event.key !== "ArrowDown")) return;
    event.preventDefault();
    onMove(event.key === "ArrowUp" ? -1 : 1);
  }

  return (
    <SidebarMenuItem
      draggable
      onDragStart={(event) => {
        event.dataTransfer.effectAllowed = "move";
        onDragStart();
      }}
      onDragEnd={onDragEnd}
      onDragOver={onDragOver}
      onDrop={onDrop}
      data-drag-preview={preview || undefined}
      className={cn(
        "group/collection rounded-md border border-transparent transition-[background-color,border-color,opacity] duration-150",
        preview && "border-dashed border-sidebar-ring bg-sidebar-accent/60 opacity-70",
      )}
    >
      <SidebarMenuButton asChild isActive={active} tooltip={collection.name} className="pr-8">
        <Link href={href} aria-current={active ? "page" : undefined} draggable={false}>
          <span
            className={cn(
              "-ml-1.5 flex size-7 shrink-0 items-center justify-center rounded-lg",
              "group-data-[collapsible=icon]:ml-0 group-data-[collapsible=icon]:size-4 group-data-[collapsible=icon]:rounded-sm",
              tint,
            )}
            aria-hidden="true"
          >
            <Database className="size-4 group-data-[collapsible=icon]:size-3" />
          </span>
          <span>{collection.name}</span>
        </Link>
      </SidebarMenuButton>
      <button
        type="button"
        aria-label={`Move ${collection.name}`}
        title="Drag, or Alt+Up/Down to move"
        onKeyDown={onGripKeyDown}
        className="absolute top-1.5 right-1 flex size-5 cursor-grab items-center justify-center rounded text-sidebar-foreground/50 opacity-0 outline-none group-hover/collection:opacity-100 hover:bg-sidebar-accent focus-visible:opacity-100 focus-visible:ring-2 focus-visible:ring-sidebar-ring group-data-[collapsible=icon]:hidden"
      >
        <GripVertical className="size-3.5" aria-hidden="true" />
      </button>
    </SidebarMenuItem>
  );
}

/** Rail toggle while collapsed, resize handle while expanded. */
function SidebarEdge() {
  const { state } = useSidebar();
  return state === "collapsed" ? <SidebarRail /> : <SidebarResizeHandle />;
}

/** Collapsible main navigation. Collections and the pending-proposal count come from the API. */
export function AppSidebar() {
  const pathname = usePathname();
  const requestedSettings = useSearchParams().get(SETTINGS_PARAM);
  const openSettings = isSettingsSection(requestedSettings) ? requestedSettings : null;
  const { data: actions } = useCountProposals({ group: "ACTIONS" }, { query: { refetchInterval: PROPOSAL_COUNT_REFRESH_MS } });
  const { data: findings } = useCountProposals({ group: "FINDINGS" }, { query: { refetchInterval: PROPOSAL_COUNT_REFRESH_MS } });
  const badges: Record<string, { count: number | undefined; label: string }> = {
    "/proposals": { count: actions?.count, label: "pending proposals" },
    "/findings": { count: findings?.count, label: "new findings" },
  };

  return (
    <Sidebar collapsible="icon">
      <SidebarHeader>
        <SidebarMenu>
          <SidebarMenuItem>
            <SidebarMenuButton asChild size="lg" tooltip="contor">
              <Link href="/inbox" aria-label="contor home">
                <span
                  className="flex aspect-square size-8 shrink-0 items-center justify-center rounded-lg bg-primary text-base font-bold text-primary-foreground shadow-sm"
                  aria-hidden="true"
                >
                  c
                </span>
                <span className="grid min-w-0 leading-tight">
                  <span className="truncate text-base font-semibold tracking-tight">contor</span>
                  <span className="truncate text-xs text-muted-foreground">Personal hub</span>
                </span>
              </Link>
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarHeader>
      <SidebarContent>
        <nav aria-label="Main" className="contents">
          <SidebarGroup>
            <SidebarGroupContent>
              <SidebarMenu>
                {PRIMARY_NAV.map((item) => (
                  <NavLink key={item.href} item={item} pathname={pathname} badge={badges[item.href]?.count} badgeLabel={badges[item.href]?.label} />
                ))}
              </SidebarMenu>
            </SidebarGroupContent>
          </SidebarGroup>
          <CollectionsGroup pathname={pathname} />
          <SidebarGroup>
            <SidebarGroupLabel>Graph & automation</SidebarGroupLabel>
            <SidebarGroupContent>
              <SidebarMenu>
                {DATA_NAV.map((item) => (
                  <NavLink key={item.href} item={item} pathname={pathname} />
                ))}
              </SidebarMenu>
            </SidebarGroupContent>
          </SidebarGroup>
          <SidebarGroup>
            <SidebarGroupContent>
              <SidebarMenu>
                <SidebarMenuItem>
                  <SidebarMenuButton asChild isActive={openSettings !== null} tooltip={SETTINGS_ROOT.title}>
                    <Link href={settingsHref(pathname, "accounts")} scroll={false}>
                      <SETTINGS_ROOT.icon aria-hidden="true" />
                      <span>{SETTINGS_ROOT.title}</span>
                    </Link>
                  </SidebarMenuButton>
                  <SidebarMenuSub>
                    {SETTINGS_NAV.map((item) => {
                      const active = openSettings === item.section;
                      return (
                        <SidebarMenuSubItem key={item.href}>
                          <SidebarMenuSubButton asChild isActive={active}>
                            <Link href={settingsHref(pathname, item.section)} scroll={false}>
                              <span>{item.title}</span>
                            </Link>
                          </SidebarMenuSubButton>
                        </SidebarMenuSubItem>
                      );
                    })}
                  </SidebarMenuSub>
                </SidebarMenuItem>
              </SidebarMenu>
            </SidebarGroupContent>
          </SidebarGroup>
        </nav>
      </SidebarContent>
      <SidebarEdge />
    </Sidebar>
  );
}
