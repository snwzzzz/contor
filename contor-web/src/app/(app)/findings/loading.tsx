import { PageHeaderSkeleton } from "@/components/app/LoadingSkeletons";
import { FindingsSkeleton } from "@/components/app/findings/FindingsSkeleton";

/** The findings page's header, filter chips and cards while its route loads. */
export default function Loading() {
  return (
    <>
      <PageHeaderSkeleton commands={4} />
      <FindingsSkeleton />
    </>
  );
}
