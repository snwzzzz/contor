import { describe, expect, it } from "vitest";
import { collectionTint, tintClass, tintTextClass } from "./collection-tint";

describe("tints", () => {
  it("maps a tint number to its background and text classes", () => {
    expect(tintClass(3)).toBe("bg-tint-3 text-tint-3-foreground");
    expect(tintTextClass(3)).toBe("text-tint-3-foreground");
  });

  it("gives every collection a stable tint from the six", () => {
    expect(collectionTint(1)).toBe(tintClass(2));
    expect(collectionTint(6)).toBe(tintClass(1));
    expect(collectionTint(7)).toBe(collectionTint(1));
  });
});
