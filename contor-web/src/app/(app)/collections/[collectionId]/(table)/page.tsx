import { notFound } from "next/navigation";
import { CollectionTable } from "@/components/app/collections/CollectionTable";

export default async function CollectionPage({ params }: PageProps<"/collections/[collectionId]">) {
  const collectionId = Number((await params).collectionId);
  if (!Number.isInteger(collectionId) || collectionId <= 0) notFound();
  return <CollectionTable collectionId={collectionId} />;
}
