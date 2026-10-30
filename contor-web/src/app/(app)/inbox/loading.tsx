import { PageHeaderSkeleton } from "@/components/app/LoadingSkeletons";
import { InboxSkeleton } from "@/components/app/mail/InboxSkeleton";

/** The inbox's header, list column and reading pane while its route loads. */
export default function Loading() {
  return (
    <>
      <PageHeaderSkeleton commands={4} />
      <InboxSkeleton />
    </>
  );
}
