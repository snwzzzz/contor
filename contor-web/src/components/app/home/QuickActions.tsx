"use client";

import { CalendarDays, Ellipsis, FolderPlus, Inbox, ListTodo, MessagesSquare, ScanSearch, Sparkles, type LucideIcon } from "lucide-react";
import Link from "next/link";
import { Button } from "@/components/ui/button";
import { openCommandPalette } from "@/lib/command-palette";
import { NEW_COLLECTION_HREF } from "@/lib/navigation";

interface QuickAction {
  label: string;
  href: string;
  icon: LucideIcon;
}

const ACTIONS: readonly QuickAction[] = [
  { label: "View mailbox", href: "/inbox", icon: Inbox },
  { label: "Open chat", href: "/chat", icon: MessagesSquare },
  { label: "Calendar", href: "/calendar", icon: CalendarDays },
  { label: "To-dos", href: "/tasks", icon: ListTodo },
  { label: "Review proposals", href: "/proposals", icon: Sparkles },
  { label: "Findings", href: "/findings", icon: ScanSearch },
  { label: "New collection", href: NEW_COLLECTION_HREF, icon: FolderPlus },
];

/** Shortcuts to the most used pages as a row of small outline buttons; "More" opens the command palette. */
export function QuickActions() {
  return (
    <section aria-labelledby="home-quick-actions" className="grid gap-2">
      <h2 id="home-quick-actions" className="sr-only">
        Quick actions
      </h2>
      <ul className="flex flex-wrap gap-2">
        {ACTIONS.map((action) => (
          <li key={action.href}>
            <Button asChild variant="outline" size="sm" className="font-normal shadow-none">
              <Link href={action.href}>
                <action.icon aria-hidden="true" data-icon="inline-start" className="text-muted-foreground" />
                {action.label}
              </Link>
            </Button>
          </li>
        ))}
        <li>
          <Button variant="ghost" size="sm" className="font-normal text-muted-foreground" onClick={openCommandPalette}>
            <Ellipsis aria-hidden="true" data-icon="inline-start" />
            More
          </Button>
        </li>
      </ul>
    </section>
  );
}
