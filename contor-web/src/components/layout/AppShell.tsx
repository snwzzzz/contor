import type { ReactNode } from "react";
import { AiSetupWizard } from "@/components/app/ai/AiSetupWizard";
import { SettingsDialog } from "./SettingsDialog";
import { TopBar } from "./TopBar";

/**
 * Top bar and main content of every signed-in page, plus the settings modal and the AI setup wizard. The navigation
 * opens from the top bar as a panel over the page; `--header-height` (globals.css) lets it start below the bar.
 */
export function AppShell({ children }: { children: ReactNode }) {
  return (
    // Fixed to the viewport: pages scroll inside <main>, so panes (e.g. the inbox) can scroll on their own.
    <div className="flex h-svh flex-col overflow-hidden">
      <TopBar />
      <main className="flex min-h-0 flex-1 flex-col gap-6 overflow-y-auto p-6">{children}</main>
      <SettingsDialog />
      <AiSetupWizard />
    </div>
  );
}
