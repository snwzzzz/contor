import type { LucideIcon } from "lucide-react";
import type { ReactNode } from "react";
import { tintVividClass, type Tint } from "@/lib/collection-tint";
import { cn } from "@/lib/utils";

/**
 * What kind of thing a card or sheet shows ("New to-do", "Reply suggestion"): a small bare icon in its color next to
 * the muted label, like the page headers' icons. No colored tile. Extra marks (e.g. an importance badge) go after it.
 */
export function KindLabel({ icon: Icon, tint, children }: { icon: LucideIcon; tint?: Tint; children: ReactNode }) {
  return (
    <span className="flex items-center gap-1.5 text-xs font-medium text-muted-foreground">
      <Icon aria-hidden="true" className={cn("size-3.5 shrink-0", tint ? tintVividClass(tint) : "text-muted-foreground")} />
      {children}
    </span>
  );
}
