import { PageHeaderSkeleton } from "@/components/app/LoadingSkeletons";
import { TasksSkeleton } from "@/components/app/tasks/TaskBoardSkeleton";

/** The to-do page's header, new to-do field and board while its route loads. */
export default function Loading() {
  return (
    <>
      <PageHeaderSkeleton commands={4} />
      <TasksSkeleton />
    </>
  );
}
