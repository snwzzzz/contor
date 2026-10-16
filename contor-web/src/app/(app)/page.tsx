import type { Metadata } from "next";
import { HomeMailboxes } from "@/components/app/home/HomeMailboxes";
import { HomeProposals } from "@/components/app/home/HomeProposals";
import { NewsFeed } from "@/components/app/home/NewsFeed";
import { PLACEHOLDER_NEWS } from "@/components/app/home/news-placeholders";
import { QuickActions } from "@/components/app/home/QuickActions";
import { PageHeader } from "@/components/app/PageHeader";

export const metadata: Metadata = { title: "Home" };

export default function Home() {
  return (
    <div className="mx-auto grid w-full max-w-4xl gap-10 py-4">
      <PageHeader title="Welcome to contor" description="What is new, what waits for you, and where to go next." />
      <QuickActions />
      <NewsFeed items={PLACEHOLDER_NEWS} />
      <HomeProposals />
      <HomeMailboxes />
    </div>
  );
}
