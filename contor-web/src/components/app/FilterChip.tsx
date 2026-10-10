import { cn } from "@/lib/utils";

interface FilterChipProps {
  label: string;
  count: number;
  /** Background class of a small dot before the label (collection tint). */
  tint?: string;
  active: boolean;
  onClick: () => void;
}

/** Toggle chip of a filter row: label and count, the active one filled. */
export function FilterChip({ label, count, tint, active, onClick }: FilterChipProps) {
  return (
    <button
      type="button"
      aria-pressed={active}
      onClick={onClick}
      className={cn(
        "inline-flex items-center gap-1.5 rounded-full border px-3 py-1 text-xs transition-colors hover:bg-accent",
        active && "border-primary bg-primary text-primary-foreground hover:bg-primary/90",
      )}
    >
      {tint ? <span className={cn("size-2 rounded-full", tint)} aria-hidden="true" /> : null}
      {label}{" "}
      <span className="tabular-nums opacity-60">{count}</span>
    </button>
  );
}
