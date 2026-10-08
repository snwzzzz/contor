import type { ReactNode } from "react";
import { AiSetupWizard } from "@/components/app/ai/AiSetupWizard";
import { SidebarInset } from "@/components/ui/sidebar";
import { AppSidebar } from "./AppSidebar";
import { ResizableSidebarProvider } from "./ResizableSidebarProvider";
import { SettingsDialog } from "./SettingsDialog";
import { TopBar } from "./TopBar";

/** Sidebar + top bar + main content of every signed-in page, plus the settings modal and the AI setup wizard. */
export function AppShell({ children }: { children: ReactNode }) {
  return (
    <ResizableSidebarProvider>
      <AppSidebar />
      {/* Fixed to the viewport: pages scroll inside <main>, so panes (e.g. the inbox) can scroll on their own. */}
      <SidebarInset className="h-svh overflow-hidden">
        <TopBar />
        <main className="flex min-h-0 flex-1 flex-col gap-6 overflow-y-auto p-6">{children}</main>
      </SidebarInset>
      <SettingsDialog />
      <AiSetupWizard />
    </ResizableSidebarProvider>
  );
}
