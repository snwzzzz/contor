"use client";

import { LogOut, Monitor, Moon, Settings, Sun } from "lucide-react";
import { useTheme } from "next-themes";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { useGetCurrentUser } from "@/lib/api/generated/endpoints/auth/auth";
import { useLogoutAndRedirect } from "@/lib/hooks/useLogoutAndRedirect";
import { settingsHref } from "@/lib/navigation";

/** Current user, theme switch, settings, logout. */
export function UserMenu() {
  const pathname = usePathname();
  const { data: user } = useGetCurrentUser();
  const { setTheme } = useTheme();
  const logout = useLogoutAndRedirect();
  const username = user?.username ?? "";

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" className="gap-2 px-2">
          <Avatar className="size-6">
            <AvatarFallback className="text-xs">{username.slice(0, 2).toUpperCase()}</AvatarFallback>
          </Avatar>
          <span className="hidden sm:inline">{username}</span>
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-56">
        <DropdownMenuLabel className="grid">
          <span>{username}</span>
          <span className="text-xs font-normal text-muted-foreground">{user?.email}</span>
        </DropdownMenuLabel>
        <DropdownMenuSeparator />
        <DropdownMenuGroup>
          <DropdownMenuItem onSelect={() => setTheme("light")}>
            <Sun aria-hidden="true" />
            Light
          </DropdownMenuItem>
          <DropdownMenuItem onSelect={() => setTheme("dark")}>
            <Moon aria-hidden="true" />
            Dark
          </DropdownMenuItem>
          <DropdownMenuItem onSelect={() => setTheme("system")}>
            <Monitor aria-hidden="true" />
            System
          </DropdownMenuItem>
        </DropdownMenuGroup>
        <DropdownMenuSeparator />
        <DropdownMenuItem asChild>
          <Link href={settingsHref(pathname, "accounts")} scroll={false}>
            <Settings aria-hidden="true" />
            Settings
          </Link>
        </DropdownMenuItem>
        <DropdownMenuItem onSelect={() => logout.mutate()} disabled={logout.isPending}>
          <LogOut aria-hidden="true" />
          Log out
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
