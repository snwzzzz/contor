"use client";

import { Braces, Plus, Settings2 } from "lucide-react";
import { usePathname, useRouter } from "next/navigation";
import { useState, type KeyboardEvent } from "react";
import { SearchField } from "@/components/app/SearchField";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Skeleton } from "@/components/ui/skeleton";
import { useListExternalApis } from "@/lib/api/generated/endpoints/external-apis/external-apis";
import type { ExternalApiDto } from "@/lib/api/generated/model/externalApiDto";
import { tintClass } from "@/lib/collection-tint";
import { apiHost, appTint, statusMark } from "@/lib/external-apis";
import { APPS_HREF, appHref, appPageHref } from "@/lib/navigation";
import { relativeTime } from "@/lib/relative-time";
import { cn } from "@/lib/utils";

/** From this many apps on, the menu gets a search field. */
const SEARCH_FROM = 9;

/** Keys the search field keeps; the rest (arrows, Escape, Tab) still move through the menu. */
const MENU_KEYS = new Set(["ArrowDown", "ArrowUp", "Escape", "Tab"]);

/**
 * The top bar's External APIs menu: the connected apps as a dense IntelliJ-style list, each leading to its page;
 * "Add app…" opens the empty modal over the current page, "Manage apps" the apps page. Arrow keys move, Enter opens
 * (Radix menu).
 */
export function AppsMenu() {
  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" size="icon" aria-label="External APIs">
          <Braces aria-hidden="true" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="start" className="w-80">
        {/* Mounted only while open: the list loads, and the clock is read, on the client when needed. */}
        <AppsMenuContent />
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

function AppsMenuContent() {
  const pathname = usePathname();
  const router = useRouter();
  const [filter, setFilter] = useState("");
  const { data: apps, isPending, isError } = useListExternalApis();

  function add() {
    router.push(appHref(pathname, "new"), { scroll: false });
  }

  function keepTyping(event: KeyboardEvent<HTMLInputElement>) {
    // Radix's typeahead would otherwise take the letters.
    if (!MENU_KEYS.has(event.key)) event.stopPropagation();
  }

  const needle = filter.trim().toLowerCase();
  const visible = needle
    ? (apps ?? []).filter((api) => api.name.toLowerCase().includes(needle) || apiHost(api.baseUrl).toLowerCase().includes(needle))
    : (apps ?? []);
  const now = new Date();

  return (
    <>
      <DropdownMenuLabel>External APIs</DropdownMenuLabel>
      <DropdownMenuSeparator />
      {apps && apps.length >= SEARCH_FROM ? (
        <div className="p-1">
          <SearchField
            value={filter}
            onChange={(event) => setFilter(event.target.value)}
            onClear={() => setFilter("")}
            onKeyDown={keepTyping}
            placeholder="Search apps"
            aria-label="Search apps"
          />
        </div>
      ) : null}
      {isPending ? (
        <div role="status" aria-label="Loading apps" className="grid gap-1 p-1">
          {["w-24", "w-32", "w-20"].map((width) => (
            <div key={width} className="flex items-center gap-2.5 px-2 py-1.5" aria-hidden="true">
              <Skeleton className="size-4 rounded-sm" />
              <div className="grid flex-1 gap-1">
                <Skeleton className={cn("h-3.5", width)} />
                <Skeleton className="h-3 w-28" />
              </div>
            </div>
          ))}
        </div>
      ) : isError ? (
        <p className="px-2 py-6 text-center text-sm text-muted-foreground">Apps could not be loaded.</p>
      ) : apps.length === 0 ? (
        <div className="grid place-items-center gap-1 px-4 py-8 text-center">
          <Braces className="mb-1 size-5 text-muted-foreground" strokeWidth={1.75} aria-hidden="true" />
          <p className="text-sm font-medium">No apps yet</p>
          <p className="text-xs text-muted-foreground">Connect a shop, a job platform or any other API.</p>
          <DropdownMenuItem asChild onSelect={add}>
            <Button variant="outline" size="xs" className="mt-2">
              Add app
            </Button>
          </DropdownMenuItem>
        </div>
      ) : (
        <>
          <div className="max-h-96 overflow-y-auto">
            {visible.map((api) => (
              <AppRow key={api.id} api={api} now={now} onSelect={() => router.push(appPageHref(api.id))} />
            ))}
            {visible.length === 0 ? <p className="px-2 py-4 text-center text-sm text-muted-foreground">No app matches.</p> : null}
          </div>
          <DropdownMenuSeparator />
          <DropdownMenuItem onSelect={add}>
            <Plus aria-hidden="true" />
            Add app…
          </DropdownMenuItem>
          <DropdownMenuItem onSelect={() => router.push(APPS_HREF)}>
            <Settings2 aria-hidden="true" />
            Manage apps
          </DropdownMenuItem>
        </>
      )}
    </>
  );
}

function AppRow({ api, now, onSelect }: { api: ExternalApiDto; now: Date; onSelect: () => void }) {
  const mark = statusMark(api.status);
  return (
    <DropdownMenuItem onSelect={onSelect} className="gap-2.5 py-1.5">
      <span
        aria-hidden="true"
        className={cn("flex size-4 shrink-0 items-center justify-center rounded-sm text-[10px] font-semibold", tintClass(appTint(api.id)))}
      >
        {api.name.charAt(0).toUpperCase()}
      </span>
      <span className="grid min-w-0 flex-1">
        <span className="flex min-w-0 items-center gap-1.5">
          <span className="truncate font-medium">{api.name}</span>
          {mark ? (
            <>
              <span aria-hidden="true" className={cn("size-1.5 shrink-0 rounded-full", mark.dot)} />
              <span className="sr-only">{mark.label}</span>
            </>
          ) : null}
        </span>
        <span className="truncate text-xs text-muted-foreground">{apiHost(api.baseUrl)}</span>
      </span>
      <span className="shrink-0 text-xs text-muted-foreground tabular-nums">
        {api.lastCalledAt ? relativeTime(api.lastCalledAt, now) : "Not called yet"}
      </span>
    </DropdownMenuItem>
  );
}
