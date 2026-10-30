"use client";

import Link from "next/link";
import type { ComponentType, SVGProps } from "react";
import { Button } from "@/components/ui/button";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { CHAT_NAV } from "@/lib/navigation";

interface IconLinkProps {
  href: string;
  label: string;
  icon: ComponentType<SVGProps<SVGSVGElement>>;
}

/** Round icon button in the top bar that navigates, with its label as tooltip. */
function IconLink({ href, label, icon: Icon }: IconLinkProps) {
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <Button asChild variant="ghost" size="icon">
          <Link href={href} aria-label={label}>
            <Icon aria-hidden="true" />
          </Link>
        </Button>
      </TooltipTrigger>
      <TooltipContent>{label}</TooltipContent>
    </Tooltip>
  );
}

export function ChatLink() {
  return <IconLink href={CHAT_NAV.href} label={CHAT_NAV.title} icon={CHAT_NAV.icon} />;
}
