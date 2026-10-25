import { Mail } from "lucide-react";

/**
 * The empty reading pane, like Outlook's "select an item to read": a framed, lightly tinted area that fills the pane
 * from the top (aligned with the list), so the pane reads as "your email shows here" instead of floating text.
 */
export function ReadingPanePlaceholder() {
  return (
    <div
      data-slot="reading-pane-placeholder"
      className="flex h-full min-h-60 flex-1 flex-col items-center justify-center gap-3 rounded-lg border border-dashed bg-muted/30 p-6 text-center"
    >
      <span aria-hidden="true" className="flex size-12 items-center justify-center rounded-full border bg-background">
        <Mail className="size-5 text-muted-foreground" strokeWidth={1.75} />
      </span>
      <div className="grid gap-0.5">
        <p className="text-sm font-medium">Select an email to read it</p>
        <p className="text-xs text-muted-foreground">Pick one from the list to see the whole conversation.</p>
      </div>
    </div>
  );
}
