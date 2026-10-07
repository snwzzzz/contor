"use client";

import { Table } from "lucide-react";
import { EmptyState } from "@/components/app/EmptyState";
import { PageHeader } from "@/components/app/PageHeader";
import { useGetCollection } from "@/lib/api/generated/endpoints/collections/collections";

/** Collection page until the table/record UI arrives (next step). */
export function CollectionPlaceholder({ collectionId }: { collectionId: number }) {
  const { data: collection } = useGetCollection(collectionId);
  return (
    <>
      <PageHeader title={collection?.name ?? "Collection"} description={collection?.description} />
      <EmptyState icon={Table} title="Records view coming next" description="The table, record form and schema editor arrive in the next step." />
    </>
  );
}
