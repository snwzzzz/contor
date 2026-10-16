import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

interface HomeSectionProps {
  title: string;
  /** Link(s) on the right of the heading, e.g. "View all". */
  action?: ReactNode;
  className?: string;
  children: ReactNode;
}

/** A titled section of the home page: a heading and its content, no box around it. */
export function HomeSection({ title, action, className, children }: HomeSectionProps) {
  const id = `home-${title.toLowerCase().replaceAll(/[^a-z]+/g, "-")}`;
  return (
    <section aria-labelledby={id} className={cn("flex flex-col gap-2", className)}>
      <header className="flex items-center justify-between gap-2">
        <h2 id={id} className="text-lg font-semibold tracking-tight">
          {title}
        </h2>
        {action}
      </header>
      {children}
    </section>
  );
}

/** Small text link in a section heading ("View all", "Manage"). */
export const SECTION_LINK = "text-xs font-medium text-primary underline-offset-4 hover:underline focus-visible:underline outline-none";
