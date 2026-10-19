"use client";

import { ChevronLeft, ChevronRight } from "lucide-react";
import { useState } from "react";
import { Button } from "@/components/ui/button";

export interface Pages<T> {
  /** The items of the current page. */
  items: readonly T[];
  /** Zero-based. */
  page: number;
  pages: number;
  setPage: (page: number) => void;
}

/** Splits `items` into pages of `size`; the page is clamped when the list shrinks (e.g. after approving). */
export function usePages<T>(items: readonly T[], size: number): Pages<T> {
  const [requested, setPage] = useState(0);
  const pages = Math.max(1, Math.ceil(items.length / size));
  const page = Math.min(requested, pages - 1);
  return { items: items.slice(page * size, page * size + size), page, pages, setPage };
}

interface PagerProps {
  /** What is paged, for the navigation's name ("Mailboxes" → "Mailboxes pages"). */
  label: string;
  page: number;
  pages: number;
  setPage: (page: number) => void;
}

/** "‹ 2 / 3 ›" under a short list, like turning the pages of a book. Hidden when everything fits on one page. */
export function Pager({ label, page, pages, setPage }: PagerProps) {
  if (pages <= 1) return null;
  return (
    <nav aria-label={`${label} pages`} className="flex items-center justify-end gap-1 text-xs text-muted-foreground">
      <Button variant="ghost" size="icon-xs" aria-label="Previous page" disabled={page === 0} onClick={() => setPage(page - 1)}>
        <ChevronLeft aria-hidden="true" />
      </Button>
      <span aria-live="polite" className="min-w-10 text-center tabular-nums">{`${page + 1} / ${pages}`}</span>
      <Button variant="ghost" size="icon-xs" aria-label="Next page" disabled={page === pages - 1} onClick={() => setPage(page + 1)}>
        <ChevronRight aria-hidden="true" />
      </Button>
    </nav>
  );
}
