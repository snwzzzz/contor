import { act, renderHook } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { useStoredValue, writeStoredValue } from "./stored-value";

const parseNumber = (raw: unknown) => {
  const value = Number(raw);
  return Number.isFinite(value) ? value : undefined;
};

describe("useStoredValue", () => {
  afterEach(() => {
    window.localStorage.clear();
    vi.restoreAllMocks();
  });

  it("falls back without a stored value and follows writes", () => {
    const { result } = renderHook(() => useStoredValue("contor.test.width", 256, parseNumber));
    expect(result.current).toBe(256);

    act(() => writeStoredValue("contor.test.width", 300));

    expect(result.current).toBe(300);
    expect(window.localStorage.getItem("contor.test.width")).toBe("300");
  });

  it("falls back on unreadable values and when storage throws", () => {
    window.localStorage.setItem("contor.test.width", "\"wide\"");
    expect(renderHook(() => useStoredValue("contor.test.width", 256, parseNumber)).result.current).toBe(256);

    vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => {
      throw new Error("blocked");
    });
    expect(renderHook(() => useStoredValue("contor.test.other", 200, parseNumber)).result.current).toBe(200);
    expect(() => writeStoredValue("contor.test.other", 1)).not.toThrow();
  });
});
