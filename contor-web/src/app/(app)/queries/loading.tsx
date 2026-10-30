import { PlaceholderPageSkeleton } from "@/components/app/LoadingSkeletons";

/** The queries page's header and empty state while its route loads. */
export default function Loading() {
  return <PlaceholderPageSkeleton label="Loading queries" commands={2} />;
}
