import { notFound } from "next/navigation";
import { RecordPage as RecordPageView } from "@/components/app/collections/RecordPage";

function positiveId(value: string): number | null {
  const id = Number(value);
  return Number.isInteger(id) && id > 0 ? id : null;
}

export default async function RecordPage({ params }: PageProps<"/collections/[collectionId]/records/[recordId]">) {
  const { collectionId, recordId } = await params;
  const collection = positiveId(collectionId);
  const record = positiveId(recordId);
  if (collection === null || record === null) notFound();
  return <RecordPageView collectionId={collection} recordId={record} />;
}
