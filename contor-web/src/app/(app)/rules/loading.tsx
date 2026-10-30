import { PlaceholderPageSkeleton } from "@/components/app/LoadingSkeletons";

/** The rules page's header and empty state while its route loads. */
export default function Loading() {
  return <PlaceholderPageSkeleton label="Loading rules" commands={2} />;
}
