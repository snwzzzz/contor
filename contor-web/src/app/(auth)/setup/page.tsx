import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { AuthCard } from "@/components/app/AuthCard";
import { SetupForm } from "@/components/app/auth/SetupForm";
import { isSetupRequired } from "@/lib/server/setup-status";

export const metadata: Metadata = { title: "Set up" };

export default async function SetupPage() {
  if (!(await isSetupRequired())) redirect("/login");

  return (
    <AuthCard title="Welcome to contor" description="Create the owner account. This page disappears afterwards.">
      <SetupForm />
    </AuthCard>
  );
}
