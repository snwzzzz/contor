import { PlaceholderPageSkeleton } from "@/components/app/LoadingSkeletons";

/** The relations page's header and empty state while its route loads. */
export default function Loading() {
  return <PlaceholderPageSkeleton label="Loading relations" commands={2} />;
}
