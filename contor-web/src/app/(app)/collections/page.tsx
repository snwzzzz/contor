import { Database } from "lucide-react";
import type { Metadata } from "next";
import { EmptyState } from "@/components/app/EmptyState";
import { PageHeader } from "@/components/app/PageHeader";

export const metadata: Metadata = { title: "Collections" };

export default function Page() {
  return (
    <>
      <PageHeader title="Collections" description="Your own data structures: deliveries, orders, people and more." />
      <EmptyState icon={Database} title="Pick a collection" description="Choose a collection in the sidebar. The collection overview arrives in the next step." />
    </>
  );
}
