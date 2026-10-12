import type { LucideIcon } from "lucide-react";
import type { ReactNode } from "react";

/** Friendly placeholder for empty lists and pages: icon tile, short explanation and optional actions. */
export function EmptyState({
  icon: Icon,
  title,
  description,
  action,
}: {
  icon?: LucideIcon;
  title: string;
  description?: string;
  action?: ReactNode;
}) {
  return (
    <section className="flex flex-col items-center justify-center gap-4 px-6 py-16 text-center animate-in fade-in duration-300 motion-reduce:animate-none">
      {Icon ? <Icon className="size-9 text-primary/80" strokeWidth={1.5} aria-hidden="true" /> : null}
      <div className="grid max-w-md gap-1.5">
        <h2 className="text-base font-semibold">{title}</h2>
        {description ? <p className="text-sm text-balance text-muted-foreground">{description}</p> : null}
      </div>
      {action ? <div className="flex flex-wrap justify-center gap-2">{action}</div> : null}
    </section>
  );
}
