import { describe, expect, it } from "vitest";
import { isApplePlatform, shortcutLabel } from "./platform";

describe("isApplePlatform", () => {
  it("recognizes macOS and iOS", () => {
    expect(isApplePlatform("MacIntel")).toBe(true);
    expect(isApplePlatform("macOS")).toBe(true);
    expect(isApplePlatform("iPad")).toBe(true);
  });

  it("treats everything else as Ctrl-based", () => {
    expect(isApplePlatform("Win32")).toBe(false);
    expect(isApplePlatform("Windows")).toBe(false);
    expect(isApplePlatform("Linux x86_64")).toBe(false);
    expect(isApplePlatform("")).toBe(false);
  });
});

describe("shortcutLabel", () => {
  it("names the modifier the way the OS does", () => {
    expect(shortcutLabel("K", true)).toBe("⌘K");
    expect(shortcutLabel("K", false)).toBe("Ctrl K");
  });
});
