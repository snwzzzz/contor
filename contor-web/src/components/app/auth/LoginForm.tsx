"use client";

import { useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { AppButton } from "@/components/app/AppButton";
import { ErrorMessage } from "@/components/app/ErrorMessage";
import { FormField } from "@/components/app/FormField";
import { getGetCurrentUserQueryKey, useLogin } from "@/lib/api/generated/endpoints/auth/auth";
import { safeNextPath } from "@/lib/auth";

/** Username/password login. On success the current user is cached and the user continues to `next`. */
export function LoginForm({ next }: { next?: string }) {
  const router = useRouter();
  const queryClient = useQueryClient();
  const [password, setPassword] = useState("");
  const login = useLogin({
    mutation: {
      // 401 here means "wrong credentials", shown inline; not "session expired".
      meta: { skipAuthRedirect: true, silent: true },
      onSuccess: (user) => {
        queryClient.setQueryData(getGetCurrentUserQueryKey(), user);
        router.replace(safeNextPath(next));
      },
      onError: () => setPassword(""),
    },
  });

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const username = new FormData(event.currentTarget).get("username");
    login.mutate({ data: { username: String(username ?? ""), password } });
  }

  return (
    <form onSubmit={handleSubmit} className="grid gap-4" noValidate={false}>
      <ErrorMessage message={login.error?.message} />
      <FormField id="username" name="username" label="Username" autoComplete="username" required autoFocus />
      <FormField
        id="password"
        name="password"
        label="Password"
        type="password"
        autoComplete="current-password"
        required
        value={password}
        onChange={(event) => setPassword(event.target.value)}
      />
      <AppButton type="submit" loading={login.isPending}>
        Log in
      </AppButton>
    </form>
  );
}
