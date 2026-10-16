"use client";

import { useState } from "react";
import { cn } from "@/lib/utils";
import { HomeSection } from "./HomeSection";

export interface NewsItem {
  id: string;
  subject: string;
  /** A short preview of what is new (e.g. the start of a new email). */
  preview: string;
  /** Who is involved (sender, recipients). */
  people: readonly string[];
  /** When it arrived, already formatted ("09:12", "Yesterday"). */
  received: string;
  unread: boolean;
}

/**
 * Recent news as a bulleted list: subject, a muted line with time and people, then the preview. New ones in bold,
 * normal weight once opened. The items are placeholders for now and "read" is only remembered on this page.
 */
export function NewsFeed({ items }: { items: readonly NewsItem[] }) {
  const [opened, setOpened] = useState<ReadonlySet<string>>(() => new Set());

  function open(id: string) {
    setOpened((current) => (current.has(id) ? current : new Set(current).add(id)));
  }

  return (
    <HomeSection title="Aktuelles">
      {items.length === 0 ? (
        <p className="py-6 text-center text-sm text-muted-foreground">Nothing new</p>
      ) : (
        <ul className="grid gap-5 pt-1">
          {items.map((item) => {
            const unread = item.unread && !opened.has(item.id);
            return (
              <li key={item.id} data-unread={unread || undefined} className="relative grid gap-1 pl-5">
                <span
                  aria-hidden="true"
                  className={cn("absolute top-2.5 left-1 size-1.5 rounded-full", unread ? "bg-foreground" : "bg-muted-foreground/50")}
                />
                <h3 className="text-base leading-snug">
                  <button
                    type="button"
                    onClick={() => open(item.id)}
                    className={cn(
                      "cursor-pointer rounded-sm text-left underline-offset-4 outline-none hover:underline focus-visible:ring-2 focus-visible:ring-ring",
                      unread ? "font-semibold text-foreground" : "font-normal text-foreground/80",
                    )}
                  >
                    {item.subject}
                  </button>
                </h3>
                <p className="text-xs text-muted-foreground tabular-nums">{`${item.received} · ${item.people.join(", ")}`}</p>
                <p className={cn("mt-1 text-sm", unread ? "text-foreground" : "text-muted-foreground")}>{item.preview}</p>
              </li>
            );
          })}
        </ul>
      )}
    </HomeSection>
  );
}
