"use client";

import { Grip, Settings } from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import type { ComponentType, SVGProps } from "react";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { CHAT_NAV, settingsHref } from "@/lib/navigation";

/** The app launcher (waffle). Other apps will be listed here; empty for now. */
export function AppLauncher() {
  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" size="icon" aria-label="Apps">
          <Grip aria-hidden="true" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="start" className="w-72">
        <DropdownMenuLabel>Apps</DropdownMenuLabel>
        <DropdownMenuSeparator />
        <div className="grid place-items-center gap-1 px-4 py-8 text-center">
          <p className="text-sm font-medium">No apps yet</p>
          <p className="text-xs text-muted-foreground">Apps you connect to contor will show up here.</p>
        </div>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

interface IconLinkProps {
  href: string;
  label: string;
  icon: ComponentType<SVGProps<SVGSVGElement>>;
  /** Settings open as a modal over the page: keep the scroll position. */
  scroll?: boolean;
}

/** Round icon button in the top bar that navigates, with its label as tooltip. */
function IconLink({ href, label, icon: Icon, scroll }: IconLinkProps) {
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <Button asChild variant="ghost" size="icon">
          <Link href={href} aria-label={label} scroll={scroll}>
            <Icon aria-hidden="true" />
          </Link>
        </Button>
      </TooltipTrigger>
      <TooltipContent>{label}</TooltipContent>
    </Tooltip>
  );
}

/** Opens the settings modal over the current page. */
export function SettingsLink() {
  const pathname = usePathname();
  return <IconLink href={settingsHref(pathname, "accounts")} label="Settings" icon={Settings} scroll={false} />;
}

/** The assistant chat (a placeholder page for now). */
export function ChatLink() {
  return <IconLink href={CHAT_NAV.href} label={CHAT_NAV.title} icon={CHAT_NAV.icon} />;
}
