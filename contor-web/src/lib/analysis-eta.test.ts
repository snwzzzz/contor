import { describe, expect, it } from "vitest";
import { formatDuration, secondsPerEmail } from "./analysis-eta";

describe("secondsPerEmail", () => {
  it("is the observed pace between two samples", () => {
    expect(secondsPerEmail({ at: 0, processed: 10 }, { at: 60_000, processed: 15 })).toBe(12);
  });

  it("is unknown until at least one email finished since the first sample", () => {
    expect(secondsPerEmail({ at: 0, processed: 10 }, { at: 60_000, processed: 10 })).toBeNull();
    expect(secondsPerEmail({ at: 0, processed: 10 }, { at: 0, processed: 12 })).toBeNull();
  });
});

describe("formatDuration", () => {
  it("rounds to readable units", () => {
    expect(formatDuration(30)).toBe("less than a minute");
    expect(formatDuration(15 * 60 + 20)).toBe("about 15 min");
    expect(formatDuration(2 * 3600 + 10 * 60)).toBe("about 2 h 10 min");
    expect(formatDuration(3 * 3600)).toBe("about 3 h");
  });
});
