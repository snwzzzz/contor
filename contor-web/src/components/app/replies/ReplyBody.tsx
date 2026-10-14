import { splitPlaceholders } from "@/lib/reply-draft";
import { cn } from "@/lib/utils";

interface ReplyBodyProps {
  body: string;
  className?: string;
}

/** A draft's plain text with its `[…]` placeholders highlighted as chips (what you still have to fill in). */
export function ReplyBody({ body, className }: ReplyBodyProps) {
  return (
    <p className={cn("text-sm leading-relaxed whitespace-pre-wrap", className)}>
      {splitPlaceholders(body).map((part, index) =>
        part.placeholder ? (
          <mark key={index} className="rounded bg-warning/20 px-1 text-foreground ring-1 ring-warning/50">
            {part.text}
          </mark>
        ) : (
          <span key={index}>{part.text}</span>
        ),
      )}
    </p>
  );
}
