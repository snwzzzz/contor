"use client";

import { LogOut, Settings } from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { useGetCurrentUser } from "@/lib/api/generated/endpoints/auth/auth";
import { useLogoutAndRedirect } from "@/lib/hooks/useLogoutAndRedirect";
import { settingsHref } from "@/lib/navigation";

/** Current user, settings (where My account and the theme live), logout. */
export function UserMenu() {
  const pathname = usePathname();
  const { data: user } = useGetCurrentUser();
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
        <DropdownMenuItem asChild>
          <Link href={settingsHref(pathname, "general")} scroll={false}>
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
