import Link from "next/link";
import { Separator } from "@/components/ui/separator";
import { HOME_HREF } from "@/lib/navigation";
import { CommandPalette } from "./CommandPalette";
import { GlobalSearch } from "./GlobalSearch";
import { ProcessingIndicator } from "./ProcessingIndicator";
import { NavDrawer } from "./NavDrawer";
import { AppLauncher, ChatLink, SettingsLink } from "./TopBarButtons";
import { UserMenu } from "./UserMenu";

/** Compact full-width bar (3rem, white, hairline below) on every signed-in page: menu, launcher, logo, search, chat, settings and account. */
export function TopBar() {
  return (
    <header className="sticky top-0 z-20 flex h-(--header-height) w-full shrink-0 items-center gap-1 border-b bg-background px-2 sm:px-3">
      <NavDrawer />
      <AppLauncher />
      <Link href={HOME_HREF} aria-label="contor home" className="ml-1 flex items-center gap-2 rounded-md px-1 py-1 outline-none focus-visible:ring-2 focus-visible:ring-ring">
        <span
          className="flex size-6 shrink-0 items-center justify-center rounded-md bg-foreground text-xs font-semibold text-background"
          aria-hidden="true"
        >
          c
        </span>
        <span className="hidden text-sm font-medium tracking-tight sm:inline">contor</span>
      </Link>
      <div className="flex flex-1 justify-center px-3">
        <GlobalSearch />
      </div>
      <div className="flex items-center gap-1">
        {/* Hidden on phones to make room; still mounted, so the palette's shortcut keeps working. */}
        <div className="hidden items-center gap-1 md:flex">
          <CommandPalette />
          <ProcessingIndicator />
          <Separator orientation="vertical" className="mx-1 h-4" />
        </div>
        <ChatLink />
        <SettingsLink />
        <UserMenu />
      </div>
    </header>
  );
}
