import { PlaceholderPageSkeleton } from "@/components/app/LoadingSkeletons";

/** The new-collection page's header and empty state while its route loads. */
export default function Loading() {
  return <PlaceholderPageSkeleton label="Loading new collection" commands={0} icon={false} />;
}
