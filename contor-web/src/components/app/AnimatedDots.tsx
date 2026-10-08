import { cn } from "@/lib/utils";

const DELAYS_MS = [0, 200, 400];

/** "…" whose dots appear one after another and fade out again: shows that something is still working. */
export function AnimatedDots({ className }: { className?: string }) {
  return (
    <span aria-hidden="true" className={cn("inline-flex", className)}>
      {DELAYS_MS.map((delay) => (
        <span key={delay} className="animate-dot motion-reduce:animate-none" style={{ animationDelay: `${delay}ms` }}>
          .
        </span>
      ))}
    </span>
  );
}
