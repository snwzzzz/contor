import { ShieldCheck } from "lucide-react";
import type { Metadata } from "next";
import { EmptyState } from "@/components/app/EmptyState";
import { PageHeader } from "@/components/app/PageHeader";

export const metadata: Metadata = { title: "Security" };

export default function Page() {
  return (
    <>
      <PageHeader title="Security" description="Password and two-factor authentication." />
      <EmptyState icon={ShieldCheck} title="Two-factor authentication coming soon" description="TOTP 2FA and recovery codes arrive in a later step." />
    </>
  );
}
