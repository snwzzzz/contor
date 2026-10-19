"use client";

import { Database } from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { CHAT_NAV, inAppHref, navItem, SETTINGS_ROOT, type NavItem } from "@/lib/navigation";

interface Destination {
  item: NavItem;
  description: string;
}

const COLLECTIONS: NavItem = { title: "Collections", href: "/collections", icon: Database, tint: 4 };

const DESTINATIONS: readonly Destination[] = [
  { item: COLLECTIONS, description: "Records the assistant keeps, grouped by type." },
  { item: navItem("/proposals"), description: "What the assistant suggests to do with your mail." },
  { item: navItem("/findings"), description: "People, organizations and data found in your mail." },
  { item: navItem("/relations"), description: "How records connect to each other." },
  { item: navItem("/queries"), description: "Saved filters across your collections." },
  { item: navItem("/rules"), description: "Sort incoming mail automatically." },
  { item: CHAT_NAV, description: "Ask the assistant about your data." },
  { item: SETTINGS_ROOT, description: "Mailboxes, AI models, autonomy and security." },
];

/** "Navigate" at the bottom of the home page: four columns of small icon, title and a one-line description. */
export function NavigateLinks() {
  const pathname = usePathname();
  return (
    <section aria-labelledby="home-navigate" className="grid gap-2">
      <h2 id="home-navigate" className="flex h-8 items-center border-b text-sm font-medium">
        Navigate
      </h2>
      <ul className="-mx-2 grid gap-x-6 sm:grid-cols-2 xl:grid-cols-4">
        {DESTINATIONS.map(({ item, description }) => (
          <li key={item.href}>
            <Link
              href={inAppHref(item, pathname)}
              scroll={item === SETTINGS_ROOT ? false : undefined}
              className="group flex items-start gap-3 rounded-md p-2 outline-none transition-colors hover:bg-muted focus-visible:bg-muted focus-visible:ring-2 focus-visible:ring-ring motion-reduce:transition-none"
            >
              <item.icon aria-hidden="true" className="mt-0.5 size-4 shrink-0 text-muted-foreground transition-colors group-hover:text-foreground" strokeWidth={1.75} />
              <span className="grid min-w-0 gap-0.5">
                <span className="text-sm underline-offset-4 group-hover:underline">{item.title}</span>
                <span className="text-xs text-muted-foreground">{description}</span>
              </span>
            </Link>
          </li>
        ))}
      </ul>
    </section>
  );
}
