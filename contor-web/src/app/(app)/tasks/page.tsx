import type { Metadata } from "next";
import { PageHeader } from "@/components/app/PageHeader";
import { TaskList } from "@/components/app/tasks/TaskList";

export const metadata: Metadata = { title: "To-dos" };

export default function Page() {
  return (
    <>
      <PageHeader title="To-dos" description="Things you have to do, from your mail and your own." />
      <TaskList />
    </>
  );
}
