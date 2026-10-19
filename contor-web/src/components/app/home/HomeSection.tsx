import { ArrowRight, type LucideIcon } from "lucide-react";
import Link from "next/link";
import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

interface HomeSectionProps {
  title: string;
  /** Link(s) on the right of the title, e.g. "View all". */
  action?: ReactNode;
  className?: string;
  children: ReactNode;
}

/** A titled section of the home page: a small heading over a hairline, then the content. No box around it. */
export function HomeSection({ title, action, className, children }: HomeSectionProps) {
  const id = `home-${title.toLowerCase().replaceAll(/[^a-z]+/g, "-")}`;
  return (
    // minmax(0, 1fr): long rows truncate instead of widening the section past its column.
    <section aria-labelledby={id} className={cn("grid min-w-0 grid-cols-[minmax(0,1fr)] content-start gap-2", className)}>
      <header className="flex h-8 items-center gap-3 border-b">
        <h2 id={id} className="text-sm font-medium">
          {title}
        </h2>
        {action ? <div className="ml-auto flex items-center gap-3">{action}</div> : null}
      </header>
      {children}
    </section>
  );
}

/** Small text link in a section header ("View all", "Manage", "Open mailbox →"): always underlined, so it reads as a link. */
export const SECTION_LINK =
  "inline-flex items-center gap-1 rounded-sm text-xs font-medium text-foreground underline decoration-muted-foreground/50 underline-offset-4 outline-none transition-colors hover:decoration-foreground focus-visible:ring-2 focus-visible:ring-ring";

/** `arrow`: a trailing "→" for links that leave the home page; `scroll={false}` for links that open a modal. */
export function SectionLink({ href, children, scroll, arrow }: { href: string; children: ReactNode; scroll?: boolean; arrow?: boolean }) {
  return (
    <Link href={href} scroll={scroll} className={SECTION_LINK}>
      {children}
      {arrow ? <ArrowRight aria-hidden="true" className="size-3" /> : null}
    </Link>
  );
}

/** A quiet empty state: muted icon, one line of title, one line of explanation, optional action. */
export function QuietEmpty({ icon: Icon, title, description, action }: { icon: LucideIcon; title: string; description?: string; action?: ReactNode }) {
  return (
    <div className="flex flex-col items-center gap-2 px-4 py-8 text-center">
      <Icon aria-hidden="true" className="size-5 text-muted-foreground" strokeWidth={1.5} />
      <div className="grid gap-0.5">
        <p className="text-sm font-medium">{title}</p>
        {description ? <p className="text-xs text-muted-foreground">{description}</p> : null}
      </div>
      {action}
    </div>
  );
}
