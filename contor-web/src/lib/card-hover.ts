/** Hover of clickable cards (proposals, findings, to-dos): the border takes the primary color and the card lifts. */
export const CARD_HOVER = "transition-[border-color,box-shadow] duration-150 hover:border-primary/40 hover:shadow-md";

/** A button over the whole card (stretched link pattern); the card needs `relative`, its other buttons `relative z-10`. */
export const STRETCHED_BUTTON = "cursor-pointer text-left after:absolute after:inset-0 after:content-['']";

/** A framed panel on the page (stat strip, activity list): hairline border, barely visible shadow. */
export const SURFACE_CARD = "rounded-lg border bg-surface-raised text-card-foreground shadow-(--shadow-card)";

/** Hover of a clickable row or cell: a quiet background, no lift. */
export const ROW_HOVER = "transition-colors duration-100 hover:bg-muted/50 motion-reduce:transition-none";
