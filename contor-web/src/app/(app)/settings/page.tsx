import type { Metadata } from "next";
import Link from "next/link";
import { PageHeader } from "@/components/app/PageHeader";
import { SETTINGS_NAV } from "@/lib/navigation";

export const metadata: Metadata = { title: "Settings" };

export default function SettingsPage() {
  return (
    <>
      <PageHeader title="Settings" description="Accounts, AI, autonomy and security." />
      <ul className="grid gap-3 sm:grid-cols-2">
        {SETTINGS_NAV.map((item) => (
          <li key={item.href}>
            <Link href={item.href} className="flex items-center gap-3 rounded-lg border p-4 hover:bg-accent">
              <item.icon className="size-5 text-muted-foreground" aria-hidden="true" />
              <span className="font-medium">{item.title}</span>
            </Link>
          </li>
        ))}
      </ul>
    </>
  );
}
