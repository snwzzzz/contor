/**
 * Manual card order on a board, stored per record as `extra.boardPosition` (a number). Cards that were never moved
 * have no position and come first, in the board's fallback order (e.g. by deadline); moved cards follow by position.
 */

/** Distance between positions when a column is numbered, leaving room to insert in between. */
const GAP = 1024;

interface Positioned {
  extra?: Record<string, unknown>;
}

export function boardPosition(item: Positioned): number | null {
  const position = item.extra?.boardPosition;
  return typeof position === "number" && Number.isFinite(position) ? position : null;
}

/** Never-moved cards first (by `fallback`), then the moved ones by position. */
export function sortForBoard<T extends Positioned>(items: readonly T[], fallback: (a: T, b: T) => number): T[] {
  return items.toSorted((a, b) => {
    const left = boardPosition(a);
    const right = boardPosition(b);
    if (left === null && right === null) return fallback(a, b);
    if (left === null) return -1;
    if (right === null) return 1;
    return left - right;
  });
}

/**
 * New positions after a card was moved: `order` is the column as it should be, with the moved card (`movedId`) in its
 * new place. Usually only the moved card changes (between its neighbours, or before/after them); when a card of the
 * column has no position yet or there is no room left, the column is numbered anew. Returns only changed positions.
 */
export function planPositions<T extends Positioned & { id: number }>(order: readonly T[], movedId: number): Map<number, number> {
  const index = order.findIndex((item) => item.id === movedId);
  const othersPositioned = order.every((item, at) => at === index || boardPosition(item) !== null);
  if (othersPositioned) {
    const before = index > 0 ? boardPosition(order[index - 1]) : null;
    const after = index < order.length - 1 ? boardPosition(order[index + 1]) : null;
    if (before === null && after === null) return new Map([[movedId, 0]]);
    if (before === null && after !== null) return new Map([[movedId, after - GAP]]);
    if (after === null && before !== null) return new Map([[movedId, before + GAP]]);
    if (before !== null && after !== null && after - before > 1e-6) return new Map([[movedId, (before + after) / 2]]);
  }
  const changed = new Map<number, number>();
  order.forEach((item, at) => {
    if (boardPosition(item) !== at * GAP) changed.set(item.id, at * GAP);
  });
  return changed;
}
