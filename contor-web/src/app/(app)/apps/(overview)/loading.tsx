import { PageHeaderSkeleton } from "@/components/app/LoadingSkeletons";
import { AppsOverviewSkeleton } from "@/components/app/apps/AppsOverview";

/** The apps page's header and table while its route loads. */
export default function Loading() {
  return (
    <>
      <PageHeaderSkeleton commands={4} />
      <AppsOverviewSkeleton />
    </>
  );
}
