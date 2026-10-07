import type { LucideIcon } from "lucide-react";
import type { ReactNode } from "react";

/** Placeholder for empty lists and not-yet-built sections. */
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
    <section className="flex flex-col items-center justify-center gap-3 rounded-lg border border-dashed px-6 py-16 text-center">
      {Icon ? <Icon className="size-8 text-muted-foreground" aria-hidden="true" /> : null}
      <h2 className="text-base font-medium">{title}</h2>
      {description ? <p className="max-w-md text-sm text-muted-foreground">{description}</p> : null}
      {action}
    </section>
  );
}
