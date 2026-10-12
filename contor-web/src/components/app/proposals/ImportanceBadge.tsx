import type { ProposalPriority } from "@/lib/api/generated/model/proposalPriority";
import { cn } from "@/lib/utils";

const STYLES: Partial<Record<ProposalPriority, { label: string; className: string }>> = {
  URGENT: { label: "Urgent", className: "bg-destructive/10 text-destructive" },
  HIGH: { label: "High", className: "bg-warning/15 text-warning" },
};

interface ImportanceBadgeProps {
  importance: ProposalPriority | undefined;
  /** Why the proposal got its importance; shown as the tooltip. */
  reason?: string;
  className?: string;
}

/** A small "Urgent" / "High" mark; nothing for normal and low importance (most proposals). */
export function ImportanceBadge({ importance, reason, className }: ImportanceBadgeProps) {
  const style = importance ? STYLES[importance] : undefined;
  if (!style) return null;
  return (
    <span title={reason} className={cn("shrink-0 rounded-full px-1.5 py-px text-[10px] font-medium", style.className, className)}>
      {style.label}
    </span>
  );
}
