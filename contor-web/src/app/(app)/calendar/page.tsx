import type { Metadata } from "next";
import { PageHeader } from "@/components/app/PageHeader";
import { CalendarView } from "@/components/app/calendar/CalendarView";

export const metadata: Metadata = { title: "Calendar" };

export default function Page() {
  return (
    <>
      <PageHeader title="Calendar" description="Appointments, interviews, deadlines and trips the assistant found in your mail." />
      <CalendarView />
    </>
  );
}
