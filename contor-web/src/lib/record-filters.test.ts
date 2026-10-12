import { describe, expect, it } from "vitest";
import type { CollectionFieldDto } from "@/lib/api/generated/model/collectionFieldDto";
import { buildFilters, countActiveFilters, filterKind } from "./record-filters";

function field(slug: string, type: CollectionFieldDto["type"], options: Record<string, unknown> = {}): CollectionFieldDto {
  return { id: 1, slug, name: slug, type, options, required: false, uniqueValue: false, position: 1 };
}

describe("filterKind", () => {
  it("picks an input per field type", () => {
    expect(filterKind(field("name", "TEXT"))).toBe("text");
    expect(filterKind(field("mail", "EMAIL"))).toBe("text");
    expect(filterKind(field("carrier", "ENUM", { values: ["DHL"] }))).toBe("choice");
    expect(filterKind(field("state", "STATUS", { states: ["open"] }))).toBe("choice");
    expect(filterKind(field("insured", "BOOLEAN"))).toBe("boolean");
    expect(filterKind(field("weight", "NUMBER"))).toBe("number");
    expect(filterKind(field("price", "MONEY"))).toBe("number");
    expect(filterKind(field("eta", "DATE"))).toBe("date");
    expect(filterKind(field("shipped", "DATETIME"))).toBe("datetime");
    expect(filterKind(field("owner", "RELATION"))).toBeNull();
    expect(filterKind(field("scan", "FILE"))).toBeNull();
  });
});

describe("buildFilters", () => {
  const fields = [
    field("name", "TEXT"),
    field("carrier", "ENUM", { values: ["DHL", "UPS"] }),
    field("insured", "BOOLEAN"),
    field("price", "MONEY"),
    field("eta", "DATE"),
    field("shipped", "DATETIME"),
  ];

  it("turns the filter inputs into API filters", () => {
    expect(
      buildFilters(fields, {
        name: { text: "  zal " },
        carrier: { equals: "DHL" },
        insured: { equals: "true" },
        price: { min: "10", max: "99.5" },
        eta: { min: "2026-03-01" },
      }),
    ).toEqual(["name:contains:zal", "carrier:eq:DHL", "insured:eq:true", "price:gte:10", "price:lte:99.5", "eta:gte:2026-03-01"]);
  });

  it("covers whole days for date-time ranges", () => {
    const [from, to] = buildFilters(fields, { shipped: { min: "2026-03-01", max: "2026-03-02" } });

    expect(from).toBe(`shipped:gte:${new Date(2026, 2, 1).toISOString()}`);
    expect(to).toBe(`shipped:lt:${new Date(2026, 2, 3).toISOString()}`);
  });

  it("ignores empty inputs, invalid numbers and unknown fields", () => {
    expect(buildFilters(fields, { name: { text: "  " }, price: { min: "abc" }, ghost: { text: "x" } })).toEqual([]);
  });
});

describe("countActiveFilters", () => {
  it("counts columns with at least one value", () => {
    expect(countActiveFilters({ a: { text: "x" }, b: { min: "", max: "3" }, c: { text: "" } })).toBe(2);
  });
});
