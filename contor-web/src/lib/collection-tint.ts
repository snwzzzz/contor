/**
 * The six tints of tokens.css (`--tint-1..6`): same lightness and chroma, only the hue differs. Class names are
 * spelled out so Tailwind finds them.
 */
export type Tint = 1 | 2 | 3 | 4 | 5 | 6;

const BOX = [
  "bg-tint-1 text-tint-1-foreground",
  "bg-tint-2 text-tint-2-foreground",
  "bg-tint-3 text-tint-3-foreground",
  "bg-tint-4 text-tint-4-foreground",
  "bg-tint-5 text-tint-5-foreground",
  "bg-tint-6 text-tint-6-foreground",
] as const;

const TEXT = [
  "text-tint-1-foreground",
  "text-tint-2-foreground",
  "text-tint-3-foreground",
  "text-tint-4-foreground",
  "text-tint-5-foreground",
  "text-tint-6-foreground",
] as const;

const VIVID = [
  "text-tint-1-vivid",
  "text-tint-2-vivid",
  "text-tint-3-vivid",
  "text-tint-4-vivid",
  "text-tint-5-vivid",
  "text-tint-6-vivid",
] as const;

/** Tinted background with matching foreground (icon boxes, chips). */
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

/** A stable tint per collection. */
export function collectionTint(collectionId: number): string {
  return BOX[Math.abs(collectionId) % BOX.length];
}

/** The tint number of a collection (same as {@link collectionTint}), e.g. for the page title icon. */
export function collectionTintNumber(collectionId: number): Tint {
  return ((Math.abs(collectionId) % BOX.length) + 1) as Tint;
}
