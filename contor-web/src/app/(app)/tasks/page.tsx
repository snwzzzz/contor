import type { Metadata } from "next";
import { CommandLink, PageCommands } from "@/components/app/PageCommands";
import { CollectionTableCommand, RefreshCommand } from "@/components/app/PageCommandButtons";
import { PageHeader } from "@/components/app/PageHeader";
import { navItem } from "@/lib/navigation";
import { TaskList } from "@/components/app/tasks/TaskList";

export const metadata: Metadata = { title: "To-dos" };

const SECTION = navItem("/tasks");

export default function Page() {
  return (
    <>
      <PageHeader
        icon={SECTION.icon}
        tint={SECTION.tint}
        title="To-dos"
        description="Things you have to do, from your mail and your own."
        actions={
          <PageCommands>
            <RefreshCommand />
            <CollectionTableCommand slug="tasks" />
            <CommandLink href="/calendar" icon={navItem("/calendar").icon} label="Calendar" />
            <CommandLink href="/proposals" icon={navItem("/proposals").icon} label="Proposals" />
          </PageCommands>
        }
      />
      <TaskList />
    </>
  );
}
