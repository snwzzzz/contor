import { describe, expect, it } from "vitest";
import { closeRecordSheetHref, parseRecordParam, parseRecordTab, recordHref, recordSheetHref, recordSheetTabHref } from "./record-sheet";

describe("record sheet addresses", () => {
  it("opens a record in the sidebar on the current page, keeping its other parameters and starting at Details", () => {
    const params = new URLSearchParams("view=month&date=2026-10-01&recordTab=emails");

    expect(recordSheetHref("/calendar", params, 3, 12)).toBe("/calendar?view=month&date=2026-10-01&record=3-12");
  });

  it("switches the sidebar's tab in place and drops it for Details (the default)", () => {
    const params = new URLSearchParams("record=3-12");

    expect(recordSheetTabHref("/calendar", params, "emails")).toBe("/calendar?record=3-12&recordTab=emails");
    expect(recordSheetTabHref("/calendar", new URLSearchParams("record=3-12&recordTab=emails"), "details")).toBe("/calendar?record=3-12");
  });

  it("closes the sidebar, keeping the page's own parameters", () => {
    expect(closeRecordSheetHref("/calendar", new URLSearchParams("view=month&record=3-12&recordTab=emails"))).toBe("/calendar?view=month");
    expect(closeRecordSheetHref("/collections/3", new URLSearchParams("record=3-12"))).toBe("/collections/3");
  });

  it("links the full page, at a tab other than Details", () => {
    expect(recordHref(3, 12)).toBe("/collections/3/records/12");
    expect(recordHref(3, 12, "details")).toBe("/collections/3/records/12");
    expect(recordHref(3, 12, "connections")).toBe("/collections/3/records/12?tab=connections");
  });

  it("reads the record and the tab from the address, ignoring anything else", () => {
    expect(parseRecordParam("3-12")).toEqual({ collectionId: 3, recordId: 12 });
    for (const raw of [null, "", "3", "3-", "-12", "a-b", "0-12", "3-1.5", "3-12-1"]) {
      expect(parseRecordParam(raw), String(raw)).toBeNull();
    }
    expect(parseRecordTab("reports")).toBe("reports");
    expect(parseRecordTab("nope")).toBe("details");
    expect(parseRecordTab(null)).toBe("details");
  });
});
