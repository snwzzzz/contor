import { Mail } from "lucide-react";

/** The empty reading pane, like Outlook's "select an item to read". */
export function ReadingPanePlaceholder() {
  return (
    <div className="flex h-full min-h-48 flex-col items-center justify-center gap-2 p-6 text-center">
      <Mail className="size-10 text-muted-foreground/50" aria-hidden="true" />
      <p className="text-sm font-medium">Select an email to read it</p>
      <p className="text-xs text-muted-foreground">Nothing is selected.</p>
    </div>
  );
}
