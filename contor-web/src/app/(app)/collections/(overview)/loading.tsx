import { PageHeaderSkeleton } from "@/components/app/LoadingSkeletons";
import { CollectionsOverviewSkeleton } from "@/components/app/collections/CollectionSkeletons";

/** The collections overview's header, search and table while its route loads. */
export default function Loading() {
  return (
    <>
      <PageHeaderSkeleton commands={4} icon={false} />
      <CollectionsOverviewSkeleton />
    </>
  );
}
