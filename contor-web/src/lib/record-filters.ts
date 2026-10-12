import type { CollectionFieldDto } from "@/lib/api/generated/model/collectionFieldDto";

/** The inputs of one column filter; which ones apply depends on the field type. */
export interface ColumnFilter {
  text?: string;
  equals?: string;
  min?: string;
  max?: string;
}

export type FilterState = Record<string, ColumnFilter>;
export type FilterKind = "text" | "choice" | "boolean" | "number" | "date" | "datetime";

/** Which filter input a field gets; references and files are not filterable. */
export function filterKind(field: CollectionFieldDto): FilterKind | null {
  switch (field.type) {
    case "TEXT":
    case "LONG_TEXT":
    case "EMAIL":
    case "URL":
    case "PHONE":
      return "text";
    case "ENUM":
    case "STATUS":
      return "choice";
    case "BOOLEAN":
      return "boolean";
    case "NUMBER":
    case "MONEY":
      return "number";
    case "DATE":
      return "date";
    case "DATETIME":
      return "datetime";
    default:
      return null;
  }
}

function present(value: string | undefined): value is string {
  return value !== undefined && value.trim() !== "";
}

function isNumber(value: string): boolean {
  return Number.isFinite(Number(value));
}

/** Start of a local calendar day as ISO-8601, `offsetDays` after the given YYYY-MM-DD. */
function dayStart(day: string, offsetDays = 0): string {
  const [year, month, date] = day.split("-").map(Number);
  return new Date(year, month - 1, date + offsetDays).toISOString();
}

/** API filters (`field:op:value`) for the filled-in inputs, in field order. */
export function buildFilters(fields: readonly CollectionFieldDto[], state: FilterState): string[] {
  const filters: string[] = [];
  for (const field of fields) {
    const input = state[field.slug];
    const kind = filterKind(field);
    if (!input || !kind) continue;
    const slug = field.slug;
    switch (kind) {
      case "text":
        if (present(input.text)) filters.push(`${slug}:contains:${input.text.trim()}`);
        break;
      case "choice":
      case "boolean":
        if (present(input.equals)) filters.push(`${slug}:eq:${input.equals}`);
        break;
      case "number":
        if (present(input.min) && isNumber(input.min)) filters.push(`${slug}:gte:${input.min.trim()}`);
        if (present(input.max) && isNumber(input.max)) filters.push(`${slug}:lte:${input.max.trim()}`);
        break;
      case "date":
        if (present(input.min)) filters.push(`${slug}:gte:${input.min}`);
        if (present(input.max)) filters.push(`${slug}:lte:${input.max}`);
        break;
      case "datetime":
        // Whole local days: from the start of the first to the start of the day after the last.
        if (present(input.min)) filters.push(`${slug}:gte:${dayStart(input.min)}`);
        if (present(input.max)) filters.push(`${slug}:lt:${dayStart(input.max, 1)}`);
        break;
    }
  }
  return filters;
}

/** Columns with at least one filled-in input. */
export function countActiveFilters(state: FilterState): number {
  return Object.values(state).filter((input) => Object.values(input).some(present)).length;
}
