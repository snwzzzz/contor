import type { LucideIcon } from "lucide-react";
import Link from "next/link";
import type { ReactNode } from "react";
import { Button } from "@/components/ui/button";

/** Small ghost buttons; the label hides on phones (icon only) but stays the accessible name. */
export const COMMAND = "font-normal text-muted-foreground hover:text-foreground";
export const COMMAND_LABEL = "sr-only md:not-sr-only";

/** The command bar of a page (Azure style): a row of quiet actions between the title and Close. */
export function PageCommands({ children }: { children: ReactNode }) {
  return (
    <div role="toolbar" aria-label="Page commands" className="flex flex-wrap items-center gap-0.5">
      {children}
    </div>
  );
}

interface CommandLinkProps {
  href: string;
  icon: LucideIcon;
  label: string;
  /** The link opens a modal over this page: keep the scroll position. */
  overlay?: boolean;
}

/** A command that goes somewhere. */
export function CommandLink({ href, icon: Icon, label, overlay }: CommandLinkProps) {
  return (
    <Button asChild variant="ghost" size="sm" className={COMMAND}>
      <Link href={href} scroll={overlay ? false : undefined}>
        <Icon aria-hidden="true" data-icon="inline-start" />
        <span className={COMMAND_LABEL}>{label}</span>
      </Link>
    </Button>
  );
}
