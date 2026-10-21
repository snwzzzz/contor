"use client";

import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useState } from "react";
import { SettingsSectionContent } from "@/components/app/settings/SettingsSectionContent";
import { Dialog, DialogContent, DialogDescription, DialogTitle } from "@/components/ui/dialog";
import { SearchField } from "@/components/app/SearchField";
import { cn } from "@/lib/utils";
import { isSettingsSection, SETTINGS_NAV, SETTINGS_PARAM, settingsHref } from "@/lib/navigation";

/** Query parameters that belong to the modal and are dropped when it closes. */
const MODAL_PARAMS = [SETTINGS_PARAM, "connected", "mailError"];

/**
 * Settings as a modal over the current page: section list on the left, the section on the right.
 * Open state lives in the URL (`?settings=<section>`), so links, back/forward and reloads all work.
 */
export function SettingsDialog() {
  const pathname = usePathname();
  const router = useRouter();
  const searchParams = useSearchParams();
  const [filter, setFilter] = useState("");

  const requested = searchParams.get(SETTINGS_PARAM);
  const section = isSettingsSection(requested) ? requested : null;
  const current = SETTINGS_NAV.find((item) => item.section === section);
  const needle = filter.trim().toLowerCase();
  const visible = needle ? SETTINGS_NAV.filter((item) => item.title.toLowerCase().includes(needle)) : SETTINGS_NAV;

  function close() {
    const remaining = new URLSearchParams(searchParams.toString());
    for (const name of MODAL_PARAMS) remaining.delete(name);
    const query = remaining.toString();
    setFilter("");
    router.replace(query ? `${pathname}?${query}` : pathname, { scroll: false });
  }

  const mailError = searchParams.get("mailError");

  return (
    <Dialog open={section !== null} onOpenChange={(open) => (open ? undefined : close())}>
      <DialogContent className="grid h-[min(85svh,52rem)] w-[min(72rem,calc(100%-2rem))] max-w-none grid-rows-[auto_1fr] gap-0 overflow-hidden p-0 sm:max-w-none md:grid-cols-[15rem_1fr] md:grid-rows-1">
        <aside className="flex flex-col gap-4 border-b bg-muted/40 p-3 md:border-r md:border-b-0">
          <SearchField
            value={filter}
            onChange={(event) => setFilter(event.target.value)}
            onClear={() => setFilter("")}
            placeholder="Search settings"
            aria-label="Search settings"
          />
          <DialogTitle className="px-2 text-xs font-medium text-muted-foreground">Settings</DialogTitle>
          <DialogDescription className="sr-only">My account, general, accounts, AI, autonomy and security.</DialogDescription>
          <nav aria-label="Settings sections">
            <ul className="grid gap-0.5">
              {visible.map((item) => {
                const active = item.section === section;
                return (
                  <li key={item.section}>
                    <Link
                      href={settingsHref(pathname, item.section)}
                      replace
                      scroll={false}
                      aria-current={active ? "page" : undefined}
                      className={cn(
                        "flex items-center gap-3 rounded-md px-2 py-1.5 text-sm hover:bg-accent",
                        active && "bg-accent font-medium",
                      )}
                    >
                      <item.icon className="size-4 text-muted-foreground" aria-hidden="true" />
                      {item.title}
                    </Link>
                  </li>
                );
              })}
            </ul>
          </nav>
        </aside>
        <div className="min-h-0 overflow-y-auto p-6 md:p-8">
          {current ? (
            <div className="grid gap-6">
              <header className="grid gap-1 pr-8">
                <h2 className="text-lg font-semibold">{current.title}</h2>
                <p className="text-sm text-muted-foreground">{current.description}</p>
              </header>
              <SettingsSectionContent
                section={current.section}
                connected={searchParams.get("connected") === "true"}
                mailError={mailError ?? undefined}
              />
            </div>
          ) : null}
        </div>
      </DialogContent>
    </Dialog>
  );
}
