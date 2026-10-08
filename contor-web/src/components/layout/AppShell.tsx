import type { ReactNode } from "react";
import { SidebarInset, SidebarProvider } from "@/components/ui/sidebar";
import { AppSidebar } from "./AppSidebar";
import { SettingsDialog } from "./SettingsDialog";
import { TopBar } from "./TopBar";

/** Sidebar + top bar + main content of every signed-in page, plus the settings modal. */
export function AppShell({ children }: { children: ReactNode }) {
  return (
    <SidebarProvider>
      <AppSidebar />
      {/* Fixed to the viewport: pages scroll inside <main>, so panes (e.g. the inbox) can scroll on their own. */}
      <SidebarInset className="h-svh overflow-hidden">
        <TopBar />
        <main className="flex min-h-0 flex-1 flex-col gap-6 overflow-y-auto p-6">{children}</main>
      </SidebarInset>
      <SettingsDialog />
    </SidebarProvider>
  );
}
