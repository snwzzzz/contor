import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";

/** Mail processing status. Placeholder until sync and the AI pipeline exist. */
export function ProcessingIndicator() {
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <span className="flex items-center gap-2 text-xs text-muted-foreground" tabIndex={0}>
          <span className="size-2 rounded-full bg-muted-foreground/50" aria-hidden="true" />
          Idle
        </span>
      </TooltipTrigger>
      <TooltipContent>Mail sync and AI processing arrive in a later phase.</TooltipContent>
    </Tooltip>
  );
}
