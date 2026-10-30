"use client";

import { Plus } from "lucide-react";
import { usePathname } from "next/navigation";
import { CommandLink } from "@/components/app/PageCommands";
import { appHref } from "@/lib/navigation";

/** Opens the add-app modal over the current page. */
export function AddAppCommand() {
  const pathname = usePathname();
  return <CommandLink href={appHref(pathname, "new")} icon={Plus} label="Add app" overlay />;
}
