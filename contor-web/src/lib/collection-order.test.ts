import { describe, expect, it } from "vitest";
import { moveItem, orderByIds } from "./collection-order";

const people = { id: 1, name: "People" };
const orgs = { id: 2, name: "Organizations" };
const events = { id: 3, name: "Calendar" };

describe("orderByIds", () => {
  it("puts saved ids first and appends new items in their original order", () => {
    expect(orderByIds([people, orgs, events], [3, 1])).toEqual([events, people, orgs]);
  });

  it("ignores saved ids that no longer exist", () => {
    expect(orderByIds([people, orgs], [9, 2])).toEqual([orgs, people]);
  });
});

describe("moveItem", () => {
  it("moves an item to a new index without changing the input", () => {
    const list = [1, 2, 3, 4];
    expect(moveItem(list, 0, 2)).toEqual([2, 3, 1, 4]);
    expect(moveItem(list, 3, 0)).toEqual([4, 1, 2, 3]);
    expect(list).toEqual([1, 2, 3, 4]);
  });

  it("clamps the target and ignores no-op moves", () => {
    expect(moveItem([1, 2, 3], 2, 9)).toEqual([1, 2, 3]);
    expect(moveItem([1, 2, 3], 0, -1)).toEqual([1, 2, 3]);
  });
});
