import { notFound } from "next/navigation";
import { AppDetail } from "@/components/app/apps/AppDetail";

export default async function AppPage({ params }: PageProps<"/apps/[apiId]">) {
  const apiId = Number((await params).apiId);
  if (!Number.isInteger(apiId) || apiId <= 0) notFound();
  return <AppDetail apiId={apiId} />;
}
