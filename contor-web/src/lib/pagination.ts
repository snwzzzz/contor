export type PageItem = number | "gap";

const SHOW_ALL_UP_TO = 7;

/**
 * Page buttons (1-based) for a pager: the first and last page and a window of three around the current one
 * (`current` is 0-based). A gap hiding a single page shows that page instead.
 */
export function pageItems(current: number, total: number): PageItem[] {
  if (total <= SHOW_ALL_UP_TO) {
    return Array.from({ length: total }, (_, index) => index + 1);
  }
  const page = current + 1;
  const windowStart = Math.max(1, Math.min(page - 1, total - 2));
  const pages = new Set([1, total, windowStart, windowStart + 1, windowStart + 2]);
  const items: PageItem[] = [];
  let previous = 0;
  for (const number of [...pages].toSorted((a, b) => a - b)) {
    if (number - previous === 2) items.push(previous + 1);
    else if (number - previous > 2) items.push("gap");
    items.push(number);
    previous = number;
  }
  return items;
}
