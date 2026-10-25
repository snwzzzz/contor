import type { Metadata } from "next";
import { AiStatusCard } from "@/components/app/home/AiStatusCard";
import { HomeHero } from "@/components/app/home/HomeHero";
import { HomeMailboxes } from "@/components/app/home/HomeMailboxes";
import { KpiRow } from "@/components/app/home/KpiRow";
import { NavigateLinks } from "@/components/app/home/NavigateLinks";
import { PendingActions } from "@/components/app/home/PendingActions";
import { RecentActivity } from "@/components/app/home/RecentActivity";
import { TodayCard } from "@/components/app/home/TodayCard";

export const metadata: Metadata = { title: "Home" };

export default function Home() {
  return (
    <div className="mx-auto grid w-full max-w-[85rem] gap-8 py-2 sm:px-2">
      <HomeHero />
      <KpiRow />
      <NavigateLinks />
      {/* Two rows on one grid, so the second row starts on one line: activity | today + mailboxes, then proposals | AI. */}
      <div className="grid grid-cols-[minmax(0,1fr)] items-start gap-x-10 gap-y-8 lg:grid-cols-[repeat(3,minmax(0,1fr))]">
        <div className="min-w-0 lg:col-span-2">
          <RecentActivity />
        </div>
        <div className="grid min-w-0 grid-cols-[minmax(0,1fr)] gap-8">
          <TodayCard />
          <HomeMailboxes />
        </div>
        <div className="min-w-0 lg:col-span-2">
          <PendingActions />
        </div>
        <div className="min-w-0">
          <AiStatusCard />
        </div>
      </div>
    </div>
  );
}
