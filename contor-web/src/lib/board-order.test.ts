import { describe, expect, it } from "vitest";
import { boardPosition, planPositions, sortForBoard } from "./board-order";

interface Item {
  id: number;
  extra: Record<string, unknown>;
  rank: number;
}

const item = (id: number, position?: number, rank = 0): Item => ({ id, rank, extra: position === undefined ? {} : { boardPosition: position } });
const byRank = (a: Item, b: Item) => a.rank - b.rank;

describe("board-order", () => {
  it("reads the stored position", () => {
    expect(boardPosition(item(1, 512))).toBe(512);
    expect(boardPosition(item(1))).toBeNull();
    expect(boardPosition({ extra: { boardPosition: "3" } })).toBeNull();
  });

  it("puts cards never moved first (by the fallback order), then moved cards by position", () => {
    const sorted = sortForBoard([item(1, 2048), item(2, undefined, 5), item(3, 0), item(4, undefined, 1)], byRank);
    expect(sorted.map((entry) => entry.id)).toEqual([4, 2, 3, 1]);
  });

  it("places a moved card between two positioned neighbours without touching them", () => {
    expect(planPositions([item(1, 0), item(9, 5000), item(2, 1024)], 9)).toEqual(new Map([[9, 512]]));
  });

  it("places a moved card before the first or after the last positioned card", () => {
    expect(planPositions([item(9), item(1, 0), item(2, 1024)], 9)).toEqual(new Map([[9, -1024]]));
    expect(planPositions([item(1, 0), item(2, 1024), item(9)], 9)).toEqual(new Map([[9, 2048]]));
    expect(planPositions([item(9)], 9)).toEqual(new Map([[9, 0]]));
  });

  it("numbers the whole column once when some cards have no position yet, writing only what changes", () => {
    expect(planPositions([item(3), item(9), item(1, 1024)], 9)).toEqual(new Map([[3, 0], [9, 1024], [1, 2048]]));
    expect(planPositions([item(3, 0), item(9), item(1)], 9)).toEqual(new Map([[9, 1024], [1, 2048]]));
  });

  it("numbers the column again when there is no room left between the neighbours", () => {
    expect(planPositions([item(1, 0), item(9), item(2, 0)], 9)).toEqual(new Map([[9, 1024], [2, 2048]]));
  });
});
