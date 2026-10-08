import type { Metadata } from "next";
import { PageHeader } from "@/components/app/PageHeader";
import { navItem } from "@/lib/navigation";
import { ProposalList } from "@/components/app/proposals/ProposalList";

export const metadata: Metadata = { title: "Proposals" };

const SECTION = navItem("/proposals");

export default function Page() {
  return (
    <>
      <PageHeader icon={SECTION.icon} tint={SECTION.tint} title="Proposals" description="Things the assistant wants to do for you: calendar entries, to-dos, new categories." />
      <ProposalList />
    </>
  );
}
