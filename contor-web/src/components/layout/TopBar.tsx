import { Separator } from "@/components/ui/separator";
import { SidebarTrigger } from "@/components/ui/sidebar";
import { CommandPalette } from "./CommandPalette";
import { GlobalSearch } from "./GlobalSearch";
import { ProcessingIndicator } from "./ProcessingIndicator";
import { UserMenu } from "./UserMenu";

export function TopBar() {
  return (
    <header className="sticky top-0 z-10 flex h-14 items-center gap-3 border-b bg-background/95 px-4 backdrop-blur">
      <SidebarTrigger aria-label="Toggle sidebar" />
      <Separator orientation="vertical" className="h-5" />
      <GlobalSearch />
      <div className="ml-auto flex items-center gap-3">
        <CommandPalette />
        <ProcessingIndicator />
        <UserMenu />
      </div>
    </header>
  );
}
