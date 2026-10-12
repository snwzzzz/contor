import type { Metadata } from "next";
import { PageHeader } from "@/components/app/PageHeader";
import { navItem } from "@/lib/navigation";
import { TaskList } from "@/components/app/tasks/TaskList";

export const metadata: Metadata = { title: "To-dos" };

const SECTION = navItem("/tasks");

export default function Page() {
  return (
    <>
      <PageHeader icon={SECTION.icon} tint={SECTION.tint} title="To-dos" description="Things you have to do, from your mail and your own." />
      <TaskList />
    </>
  );
}
