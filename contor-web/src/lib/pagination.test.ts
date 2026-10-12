import { describe, expect, it } from "vitest";
import { pageItems } from "./pagination";

describe("pageItems", () => {
  it("lists every page when there are few", () => {
    expect(pageItems(0, 5)).toEqual([1, 2, 3, 4, 5]);
    expect(pageItems(0, 1)).toEqual([1]);
    expect(pageItems(0, 0)).toEqual([]);
  });

  it("keeps the first, the last and the neighbours of the current page", () => {
    expect(pageItems(0, 12)).toEqual([1, 2, 3, "gap", 12]);
    expect(pageItems(5, 12)).toEqual([1, "gap", 5, 6, 7, "gap", 12]);
    expect(pageItems(11, 12)).toEqual([1, "gap", 10, 11, 12]);
  });

  it("does not hide a single page behind a gap", () => {
    expect(pageItems(3, 12)).toEqual([1, 2, 3, 4, 5, "gap", 12]);
  });
});
