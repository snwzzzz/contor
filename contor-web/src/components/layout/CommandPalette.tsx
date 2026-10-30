"use client";

import { Braces, Database, LogOut, Mail, Plus, Search } from "lucide-react";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useRef, useState, type FocusEvent, type KeyboardEvent as ReactKeyboardEvent } from "react";
import { Command, CommandBareInput, CommandGroup, CommandItem, CommandList, CommandSeparator } from "@/components/ui/command";
import { useListCollections } from "@/lib/api/generated/endpoints/collections/collections";
import { useListExternalApis } from "@/lib/api/generated/endpoints/external-apis/external-apis";
import { useLogoutAndRedirect } from "@/lib/hooks/useLogoutAndRedirect";
import { ALL_NAV, appPageHref, collectionHref, inAppHref, NEW_COLLECTION_HREF } from "@/lib/navigation";
import { OPEN_COMMAND_PALETTE_EVENT } from "@/lib/command-palette";
import { shortcutLabel, useIsApplePlatform } from "@/lib/platform";
import { cn } from "@/lib/utils";
import { FIELD, FIELD_FOCUS_WITHIN } from "@/components/ui/input";

/**
 * ⌘K / Ctrl+K: the app's one search. Jump to any section or collection, search the mail for what was typed (results in
 * the inbox), log out; the theme is chosen in Settings › General. It is the wide search bar in the middle of the top
 * bar: focusing it (a click, ⌘K/Ctrl+K, or `openCommandPalette()` from other parts of the app) drops the list down
 * right underneath it.
 */
export function CommandPalette() {
  const [open, setOpen] = useState(false);
  const [search, setSearch] = useState("");
  const pathname = usePathname();
  const router = useRouter();
  const logout = useLogoutAndRedirect();
  const { data: collections, isPending: collectionsLoading } = useListCollections({ query: { enabled: open } });
  const { data: apps } = useListExternalApis({ query: { enabled: open } });
  const apple = useIsApplePlatform();
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    // A closing dialog or sheet keeps its focus trap until its animation ends and pulls the focus back: keep trying for
    // a moment (about 40 frames) until the bar has it.
    function focusBar(attempt = 0) {
      const input = inputRef.current;
      if (!input) return;
      input.focus();
      if (document.activeElement !== input && attempt < 40) requestAnimationFrame(() => focusBar(attempt + 1));
    }
    function onKeyDown(event: KeyboardEvent) {
      if (event.key.toLowerCase() === "k" && (event.metaKey || event.ctrlKey)) {
        event.preventDefault();
        // Toggles: focusing the bar opens the list, leaving it closes the list.
        if (document.activeElement === inputRef.current) inputRef.current?.blur();
        else focusBar();
      }
    }
    function onOpenRequest() {
      focusBar();
    }
    document.addEventListener("keydown", onKeyDown);
    window.addEventListener(OPEN_COMMAND_PALETTE_EVENT, onOpenRequest);
    return () => {
      document.removeEventListener("keydown", onKeyDown);
      window.removeEventListener(OPEN_COMMAND_PALETTE_EVENT, onOpenRequest);
    };
  }, []);

  /** Closes the list; the input starts empty next time. */
  function close() {
    setOpen(false);
    setSearch("");
  }

  /** After a choice the bar lets go of the focus, so the next focus opens the list again. */
  function finish() {
    close();
    inputRef.current?.blur();
  }

  function onBlur(event: FocusEvent<HTMLDivElement>) {
    if (!event.currentTarget.contains(event.relatedTarget)) close();
  }

  function onKeyDown(event: ReactKeyboardEvent<HTMLDivElement>) {
    if (event.key === "Escape") {
      event.preventDefault();
      finish();
    }
  }

  function go(href: string) {
    finish();
    router.push(href);
  }

  function run(action: () => void) {
    finish();
    action();
  }

  return (
    <Command
      data-testid="search-bar"
      label="Search or jump to"
      onBlur={onBlur}
      onKeyDown={onKeyDown}
      className="relative size-auto w-full max-w-xl overflow-visible rounded-none! bg-transparent p-0"
    >
      <div className={cn("flex h-9 items-center gap-2 px-3", FIELD, FIELD_FOCUS_WITHIN)}>
        <Search aria-hidden="true" className="size-4 shrink-0 text-muted-foreground" />
        <CommandBareInput
          ref={inputRef}
          placeholder="Search pages, collections, actions or mail…"
          value={search}
          onValueChange={(value) => {
            setSearch(value);
            setOpen(true);
          }}
          onFocus={() => setOpen(true)}
          onClick={() => setOpen(true)}
        />
        <kbd className="hidden shrink-0 rounded border bg-background px-1.5 font-mono text-[10px] text-muted-foreground sm:inline">
          {shortcutLabel("K", apple)}
        </kbd>
      </div>
      {open ? (
        // Right under the bar, over the page. Pressing in the list must not blur the input (that would close it first).
        <div
          data-slot="search-dropdown"
          onMouseDown={(event) => event.preventDefault()}
          className="absolute inset-x-0 top-full z-50 mt-1 overflow-hidden rounded-lg border bg-popover p-1 text-popover-foreground shadow-md animate-in fade-in-0 duration-100 motion-reduce:animate-none"
        >
          <CommandList>
            <CommandGroup heading="Go to">
              {ALL_NAV.map((item) => (
                <CommandItem key={item.href} value={item.title} onSelect={() => go(inAppHref(item, pathname))}>
                  <item.icon aria-hidden="true" />
                  {item.title}
                </CommandItem>
              ))}
            </CommandGroup>
            <CommandSeparator />
            <CommandGroup heading="Collections">
              {collections?.map((collection) => (
                <CommandItem key={collection.id} value={`collection ${collection.name}`} onSelect={() => go(collectionHref(collection.id))}>
                  <Database aria-hidden="true" />
                  {collection.name}
                </CommandItem>
              ))}
              <CommandItem value="New collection" onSelect={() => go(NEW_COLLECTION_HREF)}>
                <Plus aria-hidden="true" />
                New collection
              </CommandItem>
            </CommandGroup>
            {apps && apps.length > 0 ? (
              <>
                <CommandSeparator />
                <CommandGroup heading="External APIs">
                  {apps.map((api) => (
                    <CommandItem key={api.id} value={`app ${api.name}`} onSelect={() => go(appPageHref(api.id))}>
                      <Braces aria-hidden="true" />
                      {api.name}
                    </CommandItem>
                  ))}
                </CommandGroup>
              </>
            ) : null}
            <CommandSeparator />
            <CommandGroup heading="Actions">
              <CommandItem value="Log out" onSelect={() => run(() => logout.mutate())}>
                <LogOut aria-hidden="true" />
                Log out
              </CommandItem>
            </CommandGroup>
            {/* Always offered for typed text, whatever else matches (forceMount skips the filter); last, so
                Enter picks a matching page or collection first and falls back to the mail search. Only once the collections
                are there: an option that is already selected stays selected when better matches arrive later. */}
            {search.trim() && !collectionsLoading ? (
              <CommandGroup heading="Mail" forceMount>
                <CommandItem value="search mail" forceMount onSelect={() => go(`/inbox?${new URLSearchParams({ q: search.trim() })}`)}>
                  <Mail aria-hidden="true" />
                  <span className="truncate">Search mail for “{search.trim()}”</span>
                </CommandItem>
              </CommandGroup>
            ) : null}
          </CommandList>
        </div>
      ) : null}
    </Command>
  );
}
