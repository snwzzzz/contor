import type { Metadata } from "next";
import { EmptyState } from "@/components/app/EmptyState";
import { PageCommands } from "@/components/app/PageCommands";
import { SettingsCommand } from "@/components/app/PageCommandButtons";
import { PageHeader } from "@/components/app/PageHeader";
import { navItem } from "@/lib/navigation";

export const metadata: Metadata = { title: "Chat" };

const SECTION = navItem("/chat");

export default function Page() {
  return (
    <>
      <PageHeader
        icon={SECTION.icon}
        tint={SECTION.tint}
        title="Chat"
        description="Talk to the assistant about your mail, calendar and to-dos."
        actions={
          <PageCommands>
            <SettingsCommand section="ai" />
            <SettingsCommand section="autonomy" />
          </PageCommands>
        }
      />
      <EmptyState icon={SECTION.icon} title="Chat is coming soon" description="You will be able to ask the assistant questions here." />
    </>
  );
}
