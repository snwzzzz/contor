"use client";

import { useQueryClient } from "@tanstack/react-query";
import { useLogout } from "@/lib/api/generated/endpoints/auth/auth";
import { hardNavigate } from "@/lib/browser";

/**
 * Ends the session and loads the login page from scratch (also if the session was already gone). A full page load
 * guarantees no cached user data stays in memory and no still-mounted query can race the redirect.
 */
export function useLogoutAndRedirect() {
  const queryClient = useQueryClient();
  return useLogout({
    mutation: {
      meta: { skipAuthRedirect: true, silent: true },
      onSettled: () => {
        queryClient.clear();
        hardNavigate("/login");
      },
    },
  });
}
