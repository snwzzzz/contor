import { notFound } from "next/navigation";
import { ReportView } from "@/components/app/reports/ReportView";

function positiveId(value: string): number | null {
  const id = Number(value);
  return Number.isInteger(id) && id > 0 ? id : null;
}

export default async function CollectionReportPage({ params }: PageProps<"/collections/[collectionId]/reports/[reportId]">) {
  const { collectionId, reportId } = await params;
  const collection = positiveId(collectionId);
  const report = positiveId(reportId);
  if (collection === null || report === null) notFound();
  return <ReportView collectionId={collection} reportId={report} />;
}
