import { PageHeaderSkeleton } from "@/components/app/LoadingSkeletons";
import { CalendarSkeleton } from "@/components/app/calendar/CalendarSkeleton";

/** The calendar's header, week grid and agenda while its route loads. */
export default function Loading() {
  return (
    <>
      <PageHeaderSkeleton commands={4} />
      <CalendarSkeleton />
    </>
  );
}
