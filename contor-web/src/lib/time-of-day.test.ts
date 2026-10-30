import { describe, expect, it } from "vitest";
import { greeting, timeOfDay } from "./time-of-day";

describe("timeOfDay", () => {
  it("splits the day into morning, afternoon and evening", () => {
    expect(timeOfDay(new Date(2026, 9, 19, 5, 0))).toBe("morning");
    expect(timeOfDay(new Date(2026, 9, 19, 11, 59))).toBe("morning");
    expect(timeOfDay(new Date(2026, 9, 19, 12, 0))).toBe("afternoon");
    expect(timeOfDay(new Date(2026, 9, 19, 17, 59))).toBe("afternoon");
    expect(timeOfDay(new Date(2026, 9, 19, 18, 0))).toBe("evening");
    expect(timeOfDay(new Date(2026, 9, 19, 2, 0))).toBe("evening");
  });
});

describe("greeting", () => {
  it("greets by time of day, or neutrally before the time is known", () => {
    expect(greeting("morning", "anna")).toBe("Good morning, anna");
    expect(greeting("evening")).toBe("Good evening");
    // Before the time of day is known (server render, hydration) the name is left out too: the user may be in the
    // client cache already while the server rendered without it, and the two must match.
    expect(greeting(null, "anna")).toBe("Welcome back");
  });
});
