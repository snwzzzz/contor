"use client";

import { QueryClientProvider } from "@tanstack/react-query";
import { ThemeProvider } from "next-themes";
import { useRouter } from "next/navigation";
import { useState, type ReactNode } from "react";
import { toast } from "sonner";
import { Toaster } from "@/components/ui/sonner";
import { TooltipProvider } from "@/components/ui/tooltip";
import { createQueryClient } from "@/lib/api/query-client";

/**
 * Client-side app context: server state (TanStack Query), theme, tooltips, toasts.
 * A 401 anywhere sends the user to the login and back afterwards.
 */
export function Providers({ nonce, children }: { nonce?: string; children: ReactNode }) {
  const router = useRouter();
  const [queryClient] = useState(() =>
    createQueryClient({
      onUnauthorized: () => {
        const { pathname, search } = window.location;
        if (pathname !== "/login") router.replace(`/login?next=${encodeURIComponent(pathname + search)}`);
      },
      notifyError: (message) => toast.error(message),
    }),
  );

  return (
    <ThemeProvider attribute="class" defaultTheme="system" enableSystem disableTransitionOnChange nonce={nonce}>
      <QueryClientProvider client={queryClient}>
        <TooltipProvider>
          {children}
          <Toaster richColors closeButton />
        </TooltipProvider>
      </QueryClientProvider>
    </ThemeProvider>
  );
}
