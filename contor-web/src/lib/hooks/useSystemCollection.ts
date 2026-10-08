import { useListCollections } from "@/lib/api/generated/endpoints/collections/collections";

/** A collection by slug (e.g. the assistant's `events` and `tasks`), from the shared collections query. */
export function useSystemCollection(slug: string) {
  const { data, isLoading, error } = useListCollections();
  return { collection: data?.find((collection) => collection.slug === slug), isLoading, error };
}
