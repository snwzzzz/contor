import type { Metadata } from "next";
import { PageHeader } from "@/components/app/PageHeader";
import { ProposalList } from "@/components/app/proposals/ProposalList";

export const metadata: Metadata = { title: "Proposals" };

export default function Page() {
  return (
    <>
      <PageHeader title="Proposals" description="Changes the AI suggests, waiting for your decision." />
      <ProposalList />
    </>
  );
}
