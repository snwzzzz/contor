import { describe, expect, it } from "vitest";
import { navItem } from "./navigation";
import { collectionTint, collectionTintNumber, recordTintNumber, tintClass, tintTextClass, tintVividClass } from "./collection-tint";

describe("tints", () => {
  it("maps a tint number to its background and text classes", () => {
    expect(tintClass(3)).toBe("bg-tint-3 text-tint-3-foreground");
    expect(tintClass(10)).toBe("bg-tint-10 text-tint-10-foreground");
    expect(tintTextClass(3)).toBe("text-tint-3-foreground");
    expect(tintVividClass(3)).toBe("text-tint-3-vivid");
  });

  it("gives every collection a stable tint from the ten", () => {
    expect(collectionTint(1)).toBe(tintClass(2));
    expect(collectionTint(10)).toBe(tintClass(1));
    expect(collectionTint(11)).toBe(collectionTint(1));
    expect(new Set([1, 2, 3, 4, 5, 6, 7, 8, 9].map((id) => collectionTintNumber(id))).size).toBe(9);
  });

  it("keeps the calendar and to-dos in their section colors", () => {
    expect(collectionTintNumber(42, "events")).toBe(navItem("/calendar").tint);
    expect(collectionTintNumber(42, "tasks")).toBe(navItem("/tasks").tint);
    expect(collectionTint(42, "people")).toBe(collectionTint(42));
  });

  it("gives records their own colors, never their collection's", () => {
    const tints = new Set<number>();
    for (let recordId = 1; recordId <= 30; recordId++) {
      const tint = recordTintNumber(1, recordId);
      expect(tint).not.toBe(collectionTintNumber(1));
      expect(recordTintNumber(1, recordId)).toBe(tint);
      tints.add(tint);
    }
    expect(tints.size).toBeGreaterThan(5);
    expect(recordTintNumber(3, 7, "events")).not.toBe(collectionTintNumber(3, "events"));
  });
});
