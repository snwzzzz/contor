import { describe, expect, it } from "vitest";
import { relativeTime } from "./relative-time";

const now = new Date("2026-10-19T12:00:00Z");

describe("relativeTime", () => {
  it("says how long ago something happened in the largest fitting unit", () => {
    expect(relativeTime("2026-10-19T11:59:40Z", now)).toBe("just now");
    expect(relativeTime("2026-10-19T11:55:00Z", now)).toBe("5 min. ago");
    expect(relativeTime("2026-10-19T09:00:00Z", now)).toBe("3 hr. ago");
    expect(relativeTime("2026-10-18T09:00:00Z", now)).toBe("yesterday");
    expect(relativeTime("2026-10-14T12:00:00Z", now)).toBe("5 days ago");
  });
});
