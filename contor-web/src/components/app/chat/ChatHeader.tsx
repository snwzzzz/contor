import { PageCommands } from "@/components/app/PageCommands";
import { SettingsCommand } from "@/components/app/PageCommandButtons";
import { PageHeader } from "@/components/app/PageHeader";
import { navItem } from "@/lib/navigation";

const SECTION = navItem("/chat");

/** The chat pages' header (start and conversation): the same title, commands and description. */
export function ChatHeader() {
  return (
    <PageHeader
      icon={SECTION.icon}
      tint={SECTION.tint}
      title="Chat"
      description="Ask the assistant about your collections, people, connections, mail, calendar and to-dos."
      actions={
        <PageCommands>
          <SettingsCommand section="ai" />
          <SettingsCommand section="autonomy" />
        </PageCommands>
      }
    />
  );
}
