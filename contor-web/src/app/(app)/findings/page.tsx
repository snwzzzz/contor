import type { Metadata } from "next";
import { PageHeader } from "@/components/app/PageHeader";
import { navItem } from "@/lib/navigation";
import { FindingsList } from "@/components/app/findings/FindingsList";

export const metadata: Metadata = { title: "Findings" };

const SECTION = navItem("/findings");

export default function Page() {
  return (
    <>
      <PageHeader icon={SECTION.icon} tint={SECTION.tint} title="Findings" description="People, organizations and records the assistant found in your mail. Keep what is right." />
      <FindingsList />
    </>
  );
}
