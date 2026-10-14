"use client";

import { useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { useEffect, useRef } from "react";
import { ErrorMessage } from "@/components/app/ErrorMessage";
import { getGetCurrentUserQueryKey } from "@/lib/api/generated/endpoints/auth/auth";
import { useDevLogin } from "@/lib/api/generated/endpoints/dev/dev";
import { safeNextPath } from "@/lib/auth";
import { LoginForm } from "./LoginForm";

/**
 * Local development: signs in as the backend's dev account right away (only offered when the dev backend says so),
 * then continues to `next`. Falls back to the normal form if that fails.
 */
export function DevAutoLogin({ username, next }: { username: string; next?: string }) {
  const router = useRouter();
  const queryClient = useQueryClient();
  const started = useRef(false);
  const devLogin = useDevLogin({
    mutation: {
      meta: { skipAuthRedirect: true, silent: true },
      onSuccess: (user) => {
        queryClient.setQueryData(getGetCurrentUserQueryKey(), user);
        router.replace(safeNextPath(next));
      },
    },
  });
  const { mutate } = devLogin;

  // Runs once on mount (the ref keeps React's dev double-invocation from signing in twice).
  useEffect(() => {
    if (started.current) return;
    started.current = true;
    mutate();
  }, [mutate]);

  if (devLogin.isError) {
    return (
      <div className="grid gap-4">
        <ErrorMessage message={devLogin.error.message} />
        <LoginForm next={next} />
      </div>
    );
  }
  return <p className="text-sm text-muted-foreground">Signing in as {username}…</p>;
}
