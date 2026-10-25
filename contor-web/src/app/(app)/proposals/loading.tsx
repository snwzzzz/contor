import { PageHeaderSkeleton } from "@/components/app/LoadingSkeletons";
import { ProposalsSkeleton } from "@/components/app/proposals/ProposalsSkeleton";

/** The proposals page's header, filters and cards while its route loads. */
export default function Loading() {
  return (
    <>
      <PageHeaderSkeleton commands={4} />
      <ProposalsSkeleton />
    </>
  );
}
