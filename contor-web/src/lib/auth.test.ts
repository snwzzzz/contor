import { describe, expect, it } from "vitest";
import { isPublicPath, safeNextPath } from "./auth";

describe("safeNextPath", () => {
  it("keeps internal paths including query strings", () => {
    expect(safeNextPath("/collections/3?page=2")).toBe("/collections/3?page=2");
  });

  it.each([null, undefined, "", "https://evil.example", "//evil.example", "/\\evil.example", "javascript:alert(1)", "/login", "/setup"])(
    "falls back for %s",
    (next) => {
      expect(safeNextPath(next)).toBe("/");
    },
  );
});

describe("isPublicPath", () => {
  it("knows the pages that work without a session", () => {
    expect(isPublicPath("/login")).toBe(true);
    expect(isPublicPath("/setup")).toBe(true);
    expect(isPublicPath("/inbox")).toBe(false);
    expect(isPublicPath("/loginx")).toBe(false);
  });
});
