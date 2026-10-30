import { act, renderHook } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { formatClock, formatTime, parseClock, setTimeFormat, TIME_FORMAT_KEY, useTimeFormat } from "./time-format";

describe("time format", () => {
  afterEach(() => window.localStorage.clear());

  it("formats as 24-hour time by default, 12-hour with AM/PM on request", () => {
    expect(formatClock("14:05", "24h")).toBe("14:05");
    expect(formatClock("09:00", "24h")).toBe("09:00");
    expect(formatClock("14:05", "12h")).toBe("2:05 PM");
    expect(formatClock("00:30", "12h")).toBe("12:30 AM");
    expect(formatClock("12:00", "12h")).toBe("12:00 PM");
    expect(formatClock("", "12h")).toBe("");
    expect(formatTime(new Date(2026, 9, 9, 22, 7), "24h")).toBe("22:07");
    expect(formatTime(new Date(2026, 9, 9, 22, 7), "12h")).toBe("10:07 PM");
  });

  it("reads typed times in either format", () => {
    expect(parseClock("14:30")).toBe("14:30");
    expect(parseClock("1430")).toBe("14:30");
    expect(parseClock("930")).toBe("09:30");
    expect(parseClock("9")).toBe("09:00");
    expect(parseClock("9.15")).toBe("09:15");
    expect(parseClock("2pm")).toBe("14:00");
    expect(parseClock("2:30 PM")).toBe("14:30");
    expect(parseClock("12 am")).toBe("00:00");
    expect(parseClock("12:15p.m.")).toBe("12:15");
    expect(parseClock(" 07:05 ")).toBe("07:05");
  });

  it("rejects what is not a time of day", () => {
    for (const text of ["", "24:00", "12:60", "13pm", "0am", "abc", "1:2"]) {
      expect(parseClock(text), text).toBeNull();
    }
  });

  it("remembers the chosen format, 24-hour when nothing is stored", () => {
    const { result } = renderHook(() => useTimeFormat());
    expect(result.current).toBe("24h");

    act(() => setTimeFormat("12h"));

    expect(result.current).toBe("12h");
    expect(window.localStorage.getItem(TIME_FORMAT_KEY)).toBe("\"12h\"");
  });
});
