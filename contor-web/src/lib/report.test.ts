import { describe, expect, it } from "vitest";
import type { ReportSourceDto } from "@/lib/api/generated/model/reportSourceDto";
import { highlightQuote, inlineMarks, isGenerating, isListLabel, reportHref, reportStep, sourceHref, sourcesLabel } from "./report";

function source(overrides: Partial<ReportSourceDto>): ReportSourceDto {
  return { marker: 1, kind: "RECORD", label: "Orders", sourceState: "CURRENT", ...overrides };
}

describe("report helpers", () => {
  it("treats a list item that is only bold text as a label of the items after it", () => {
    expect(isListLabel({ text: "**Tuesday, 2026-10-27**", markers: [] })).toBe(true);
    expect(isListLabel({ text: "**Tuesday:**", markers: [] })).toBe(true);
    expect(isListLabel({ text: "**Packaging:** the box was weak", markers: [] })).toBe(false);
    // A cited item states something: it stays an item.
    expect(isListLabel({ text: "**SO-26-0149**", markers: [1] })).toBe(false);
    expect(isListLabel({ text: "Plain text", markers: [] })).toBe(false);
  });

  it("knows which states are still being generated", () => {
    expect(isGenerating("QUEUED")).toBe(true);
    expect(isGenerating("FACT_CHECKING")).toBe(true);
    expect(isGenerating("READY")).toBe(false);
    expect(isGenerating("FAILED")).toBe(false);
  });

  it("splits bold, italic and code out of a sentence and leaves lone asterisks alone", () => {
    expect(inlineMarks("**Packaging:** the box was weak")).toEqual([
      { text: "Packaging:", mark: "strong" },
      { text: " the box was weak" },
    ]);
    expect(inlineMarks("a *rough* ride for `SO-26-0156`")).toEqual([
      { text: "a " }, { text: "rough", mark: "em" }, { text: " ride for " }, { text: "SO-26-0156", mark: "code" },
    ]);
    expect(inlineMarks("2 * 3 = 6")).toEqual([{ text: "2 * 3 = 6" }]);
    expect(inlineMarks("")).toEqual([]);
  });

  it("builds the report page link below its record", () => {
    expect(reportHref(1, 4711, 5)).toBe("/collections/1/records/4711/reports/5");
  });

  it("builds the page link of a collection report below its collection", () => {
    expect(reportHref(1, undefined, 6)).toBe("/collections/1/reports/6");
  });

  it("says what the assistant is doing, for a record or a whole collection", () => {
    expect(reportStep("COLLECTING", false)).toBe("Collecting the record, its connections and emails…");
    expect(reportStep("COLLECTING", true)).toBe("Choosing and computing the figures…");
    expect(reportStep("DRAFTING", true)).toBe("Writing the report…");
  });

  it("links each source to its exact place in contor", () => {
    expect(sourceHref(source({ kind: "FIELD", collectionId: 1, recordId: 4711, fieldSlug: "expected_delivery" })))
      .toBe("/collections/1/records/4711?tab=details&field=expected_delivery");
    expect(sourceHref(source({ kind: "RECORD", collectionId: 2, recordId: 88 }))).toBe("/collections/2/records/88");
    expect(sourceHref(source({ kind: "RELATION", collectionId: 1, recordId: 4711, relationId: 204 })))
      .toBe("/collections/1/records/4711?tab=connections&relation=204");
    expect(sourceHref(source({ kind: "EMAIL_PARAGRAPH", emailId: 7, paragraphIndex: 1 }))).toBe("/inbox?email=7&paragraph=1");
  });

  it("does not link sources that are gone or not the viewer's", () => {
    expect(sourceHref(source({ kind: "RECORD", collectionId: 2, recordId: 88, sourceState: "DELETED" }))).toBeUndefined();
    expect(sourceHref(source({ kind: "EMAIL_PARAGRAPH", sourceState: "UNAVAILABLE" }))).toBeUndefined();
  });

  it("says how many sentences have a source", () => {
    expect(sourcesLabel(18, 19)).toBe("18 of 19 sentences with a source");
    expect(sourcesLabel(1, 1)).toBe("1 of 1 sentence with a source");
    expect(sourcesLabel(0, 0)).toBe("No sentences");
  });

  it("finds a quote in its paragraph ignoring case and whitespace", () => {
    expect(highlightQuote("Your shipment is held at  customs pending documents.", "held at customs")).toEqual({
      before: "Your shipment is ",
      match: "held at  customs",
      after: " pending documents.",
    });
    expect(highlightQuote("Your shipment.", "elsewhere")).toBeUndefined();
    expect(highlightQuote("Your shipment.", undefined)).toBeUndefined();
  });
});
