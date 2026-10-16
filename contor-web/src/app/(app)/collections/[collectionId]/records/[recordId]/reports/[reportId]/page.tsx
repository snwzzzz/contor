import { notFound } from "next/navigation";
import { ReportView } from "@/components/app/reports/ReportView";

function positiveId(value: string): number | null {
  const id = Number(value);
  return Number.isInteger(id) && id > 0 ? id : null;
}

export default async function ReportPage({ params }: PageProps<"/collections/[collectionId]/records/[recordId]/reports/[reportId]">) {
  const { collectionId, recordId, reportId } = await params;
  const collection = positiveId(collectionId);
  const record = positiveId(recordId);
  const report = positiveId(reportId);
  if (collection === null || record === null || report === null) notFound();
  return <ReportView collectionId={collection} recordId={record} reportId={report} />;
}
