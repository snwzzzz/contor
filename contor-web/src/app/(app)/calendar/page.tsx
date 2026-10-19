import type { Metadata } from "next";
import { CommandLink, PageCommands } from "@/components/app/PageCommands";
import { CollectionTableCommand, RefreshCommand } from "@/components/app/PageCommandButtons";
import { PageHeader } from "@/components/app/PageHeader";
import { navItem } from "@/lib/navigation";
import { CalendarView } from "@/components/app/calendar/CalendarView";

export const metadata: Metadata = { title: "Calendar" };

const SECTION = navItem("/calendar");

export default function Page() {
  return (
    <>
      <PageHeader
        icon={SECTION.icon}
        tint={SECTION.tint}
        title="Calendar"
        description="Appointments, interviews, deadlines and trips the assistant found in your mail."
        actions={
          <PageCommands>
            <RefreshCommand />
            <CollectionTableCommand slug="events" />
            <CommandLink href="/tasks" icon={navItem("/tasks").icon} label="To-dos" />
            <CommandLink href="/proposals" icon={navItem("/proposals").icon} label="Proposals" />
          </PageCommands>
        }
      />
      <CalendarView />
    </>
  );
}
