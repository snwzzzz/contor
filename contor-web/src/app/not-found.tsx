import Link from "next/link";
import { EmptyState } from "@/components/app/EmptyState";

export default function NotFound() {
  return (
    <main className="flex min-h-svh items-center justify-center p-6">
      <EmptyState title="Page not found" description="This page does not exist." action={<Link href="/inbox" className="underline">Back to the inbox</Link>} />
    </main>
  );
}
