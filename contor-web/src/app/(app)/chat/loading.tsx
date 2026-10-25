import { PlaceholderPageSkeleton } from "@/components/app/LoadingSkeletons";

/** The chat page's header and empty state while its route loads. */
export default function Loading() {
  return <PlaceholderPageSkeleton label="Loading chat" commands={2} />;
}
