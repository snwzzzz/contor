import type { LucideIcon } from "lucide-react";
import type { ReactNode } from "react";
import { tintClass, type Tint } from "@/lib/collection-tint";
import { cn } from "@/lib/utils";

interface PageHeaderProps {
  title: string;
  description?: string;
  actions?: ReactNode;
  /** Section icon, shown in a tinted box next to the title. */
  icon?: LucideIcon;
  tint?: Tint;
}

/** Title row of every page: h1, optional section icon, description and actions. */
export function PageHeader({ title, description, actions, icon: Icon, tint }: PageHeaderProps) {
  return (
    <header className="flex flex-wrap items-start justify-between gap-4">
      <div className="flex items-start gap-3">
        {Icon ? (
          <span
            data-slot="page-icon"
            aria-hidden="true"
            className={cn("mt-0.5 flex size-9 shrink-0 items-center justify-center rounded-lg", tint ? tintClass(tint) : "bg-muted")}
          >
            <Icon className="size-5" />
          </span>
        ) : null}
        <div className="grid gap-1">
          <h1 className="text-2xl font-semibold tracking-tight">{title}</h1>
          {description ? <p className="text-sm text-muted-foreground">{description}</p> : null}
        </div>
      </div>
      {actions ? <div className="flex items-center gap-2">{actions}</div> : null}
    </header>
  );
}
