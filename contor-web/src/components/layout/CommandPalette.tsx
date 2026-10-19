"use client";

import { Database, LogOut, Monitor, Moon, Plus, Search, Sun } from "lucide-react";
import { useTheme } from "next-themes";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { AppButton } from "@/components/app/AppButton";
import {
  Command,
  CommandDialog,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
  CommandSeparator,
} from "@/components/ui/command";
import { useListCollections } from "@/lib/api/generated/endpoints/collections/collections";
import { useLogoutAndRedirect } from "@/lib/hooks/useLogoutAndRedirect";
import { ALL_NAV, collectionHref, inAppHref, NEW_COLLECTION_HREF } from "@/lib/navigation";
import { OPEN_COMMAND_PALETTE_EVENT } from "@/lib/command-palette";
import { shortcutLabel, useIsApplePlatform } from "@/lib/platform";

/**
 * ⌘K / Ctrl+K: jump to any section or collection, switch theme, log out. One instance lives in the top bar; other parts
 * of the app open it with `openCommandPalette()`.
 */
export function CommandPalette() {
  const [open, setOpen] = useState(false);
  const pathname = usePathname();
  const router = useRouter();
  const { setTheme } = useTheme();
  const logout = useLogoutAndRedirect();
  const { data: collections } = useListCollections({ query: { enabled: open } });
  const apple = useIsApplePlatform();

  useEffect(() => {
    function onKeyDown(event: KeyboardEvent) {
      if (event.key.toLowerCase() === "k" && (event.metaKey || event.ctrlKey)) {
        event.preventDefault();
        setOpen((current) => !current);
      }
    }
    function onOpenRequest() {
      setOpen(true);
    }
    document.addEventListener("keydown", onKeyDown);
    window.addEventListener(OPEN_COMMAND_PALETTE_EVENT, onOpenRequest);
    return () => {
      document.removeEventListener("keydown", onKeyDown);
      window.removeEventListener(OPEN_COMMAND_PALETTE_EVENT, onOpenRequest);
    };
  }, []);

  function go(href: string) {
    setOpen(false);
    router.push(href);
  }

  function run(action: () => void) {
    setOpen(false);
    action();
  }

  return (
    <>
      <AppButton variant="outline" className="w-56 justify-start bg-muted/40 font-normal text-muted-foreground shadow-none" onClick={() => setOpen(true)}>
        <Search aria-hidden="true" data-icon="inline-start" />
        <span>Search or jump to…</span>
        <kbd className="ml-auto rounded border bg-background px-1.5 font-mono text-[10px]">{shortcutLabel("K", apple)}</kbd>
      </AppButton>
      <CommandDialog open={open} onOpenChange={setOpen} title="Command palette" description="Jump to a page or collection, or run an action">
        <Command>
          <CommandInput placeholder="Search pages, collections and actions…" />
          <CommandList>
            <CommandEmpty>No results.</CommandEmpty>
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
            <CommandSeparator />
            <CommandGroup heading="Actions">
              <CommandItem value="Theme light" onSelect={() => run(() => setTheme("light"))}>
                <Sun aria-hidden="true" />
                Light theme
              </CommandItem>
              <CommandItem value="Theme dark" onSelect={() => run(() => setTheme("dark"))}>
                <Moon aria-hidden="true" />
                Dark theme
              </CommandItem>
              <CommandItem value="Theme system" onSelect={() => run(() => setTheme("system"))}>
                <Monitor aria-hidden="true" />
                System theme
              </CommandItem>
              <CommandItem value="Log out" onSelect={() => run(() => logout.mutate())}>
                <LogOut aria-hidden="true" />
                Log out
              </CommandItem>
            </CommandGroup>
          </CommandList>
        </Command>
      </CommandDialog>
    </>
  );
}
