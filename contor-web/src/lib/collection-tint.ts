/**
 * The ten tints of tokens.css (`--tint-1..10`): same lightness and chroma, only the hue changes
 * (red, orange, amber, lime, green, teal, sky, blue, violet, pink). Class names are spelled out so Tailwind finds them.
 */
export type Tint = 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10;

const TINT_COUNT = 10;

/** Section colors shared by the navigation and the collections behind those sections. */
export const CALENDAR_TINT: Tint = 1;
export const TASKS_TINT: Tint = 5;
const COLLECTION_SECTION_TINTS: Record<string, Tint> = { events: CALENDAR_TINT, tasks: TASKS_TINT };

const BOX = [
  "bg-tint-1 text-tint-1-foreground",
  "bg-tint-2 text-tint-2-foreground",
  "bg-tint-3 text-tint-3-foreground",
  "bg-tint-4 text-tint-4-foreground",
  "bg-tint-5 text-tint-5-foreground",
  "bg-tint-6 text-tint-6-foreground",
  "bg-tint-7 text-tint-7-foreground",
  "bg-tint-8 text-tint-8-foreground",
  "bg-tint-9 text-tint-9-foreground",
  "bg-tint-10 text-tint-10-foreground",
] as const;

const TEXT = [
  "text-tint-1-foreground",
  "text-tint-2-foreground",
  "text-tint-3-foreground",
  "text-tint-4-foreground",
  "text-tint-5-foreground",
  "text-tint-6-foreground",
  "text-tint-7-foreground",
  "text-tint-8-foreground",
  "text-tint-9-foreground",
  "text-tint-10-foreground",
] as const;

const VIVID = [
  "text-tint-1-vivid",
  "text-tint-2-vivid",
  "text-tint-3-vivid",
  "text-tint-4-vivid",
  "text-tint-5-vivid",
  "text-tint-6-vivid",
  "text-tint-7-vivid",
  "text-tint-8-vivid",
  "text-tint-9-vivid",
  "text-tint-10-vivid",
] as const;

/** Tinted background with matching foreground (icon boxes, chips, banners). */
export function tintClass(tint: Tint): string {
  return BOX[tint - 1];
}

/** Only the tint's foreground color (icons on a neutral background). */
export function tintTextClass(tint: Tint): string {
  return TEXT[tint - 1];
}

/** The bright variant of the tint for small icons on a neutral background (sidebar sections). */
export function tintVividClass(tint: Tint): string {
  return VIVID[tint - 1];
}

/** A collection's tint: its section color for the calendar and to-dos, otherwise one of the ten by id. */
export function collectionTintNumber(collectionId: number, slug?: string): Tint {
  const section = slug ? COLLECTION_SECTION_TINTS[slug] : undefined;
  return section ?? (((Math.abs(collectionId) % TINT_COUNT) + 1) as Tint);
}

export function collectionTint(collectionId: number, slug?: string): string {
  return tintClass(collectionTintNumber(collectionId, slug));
}

/**
 * A record's own tint (banner, initial): spread over the ten by id and never the collection's tint, so the records of
 * one collection do not all share its color.
 */
export function recordTintNumber(collectionId: number, recordId: number, slug?: string): Tint {
  const own = collectionTintNumber(collectionId, slug);
  // Pick from the nine other tints: an offset of 1..9 from the collection's tint.
  const offset = (Math.imul(Math.abs(recordId) + 1, 2654435761) >>> 0) % (TINT_COUNT - 1) + 1;
  return ((((own - 1) + offset) % TINT_COUNT) + 1) as Tint;
}
