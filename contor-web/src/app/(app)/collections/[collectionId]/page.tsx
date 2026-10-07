import { notFound } from "next/navigation";
import { CollectionPlaceholder } from "@/components/app/collections/CollectionPlaceholder";

export default async function CollectionPage({ params }: PageProps<"/collections/[collectionId]">) {
  const collectionId = Number((await params).collectionId);
  if (!Number.isInteger(collectionId) || collectionId <= 0) notFound();
  return <CollectionPlaceholder collectionId={collectionId} />;
}
