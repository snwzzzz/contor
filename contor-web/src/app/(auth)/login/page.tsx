import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { AuthCard } from "@/components/app/AuthCard";
import { LoginForm } from "@/components/app/auth/LoginForm";
import { isSetupRequired } from "@/lib/server/setup-status";

export const metadata: Metadata = { title: "Log in" };

export default async function LoginPage({ searchParams }: PageProps<"/login">) {
  const [setupRequired, params] = await Promise.all([isSetupRequired(), searchParams]);
  if (setupRequired) redirect("/setup");
  const next = typeof params.next === "string" ? params.next : undefined;

  return (
    <AuthCard title="Log in" description="Sign in to your contor instance.">
      <LoginForm next={next} />
    </AuthCard>
  );
}
