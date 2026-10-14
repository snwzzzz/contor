import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { AuthCard } from "@/components/app/AuthCard";
import { DevAutoLogin } from "@/components/app/auth/DevAutoLogin";
import { LoginForm } from "@/components/app/auth/LoginForm";
import { devLoginUsername } from "@/lib/server/dev-login";
import { isSetupRequired } from "@/lib/server/setup-status";

export const metadata: Metadata = { title: "Log in" };

export default async function LoginPage({ searchParams }: PageProps<"/login">) {
  const [setupRequired, params, devUsername] = await Promise.all([isSetupRequired(), searchParams, devLoginUsername()]);
  if (setupRequired) redirect("/setup");
  const next = typeof params.next === "string" ? params.next : undefined;

  return (
    <AuthCard title="Log in" description="Sign in to your contor instance.">
      {devUsername ? <DevAutoLogin username={devUsername} next={next} /> : <LoginForm next={next} />}
    </AuthCard>
  );
}
