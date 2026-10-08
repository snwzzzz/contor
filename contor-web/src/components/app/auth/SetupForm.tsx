"use client";

import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { toast } from "sonner";
import { AppButton } from "@/components/app/AppButton";
import { ErrorMessage } from "@/components/app/ErrorMessage";
import { FormField } from "@/components/app/FormField";
import { useCompleteSetup } from "@/lib/api/generated/endpoints/setup/setup";

const EMAIL_RULE = "Any Outlook address (outlook, hotmail, live or msn).";
const PASSWORD_RULES = "12–128 characters with lowercase, uppercase, digit and special character.";

/** First-run setup: creates the OWNER with the one-time token from the backend log. */
export function SetupForm() {
  const router = useRouter();
  const [mismatch, setMismatch] = useState(false);
  const setup = useCompleteSetup({
    mutation: {
      meta: { skipAuthRedirect: true, silent: true },
      onSuccess: () => {
        toast.success("Owner account created. Please log in.");
        router.replace("/login");
      },
    },
  });

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const value = (name: string) => String(form.get(name) ?? "");
    if (value("password") !== value("confirm")) {
      setMismatch(true);
      return;
    }
    setMismatch(false);
    setup.mutate({
      data: { token: value("token").trim(), username: value("username"), email: value("email"), password: value("password") },
    });
  }

  return (
    <form onSubmit={handleSubmit} className="grid gap-4">
      <ErrorMessage message={setup.error?.message} />
      <FormField
        id="token"
        name="token"
        label="Setup token"
        description="Printed once in the backend log on first start."
        autoComplete="off"
        required
        autoFocus
      />
      <FormField id="username" name="username" label="Username" autoComplete="username" required />
      <FormField
        id="email"
        name="email"
        label="Email"
        type="email"
        autoComplete="email"
        description={EMAIL_RULE}
        required
      />
      <FormField
        id="password"
        name="password"
        label="Password"
        type="password"
        autoComplete="new-password"
        description={PASSWORD_RULES}
        required
      />
      <FormField
        id="confirm"
        name="confirm"
        label="Confirm password"
        type="password"
        autoComplete="new-password"
        error={mismatch ? "Passwords do not match" : undefined}
        required
      />
      <AppButton type="submit" loading={setup.isPending}>
        Create owner account
      </AppButton>
    </form>
  );
}
