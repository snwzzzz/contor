import { QueryClientProvider, type QueryClient } from "@tanstack/react-query";
import { render, type RenderOptions } from "@testing-library/react";
import type { ReactElement, ReactNode } from "react";
import { TooltipProvider } from "@/components/ui/tooltip";
import { createQueryClient } from "@/lib/api/query-client";

export interface ProvidersOptions {
  queryClient?: QueryClient;
  onUnauthorized?: () => void;
  notifyError?: (message: string) => void;
}

export function testQueryClient(options: ProvidersOptions = {}) {
  return createQueryClient({
    onUnauthorized: options.onUnauthorized ?? (() => {}),
    notifyError: options.notifyError ?? (() => {}),
    retry: false,
  });
}

/** Renders with a fresh QueryClient (no retries) and the providers the app shell uses. */
export function renderWithProviders(ui: ReactElement, options: ProvidersOptions & RenderOptions = {}) {
  const queryClient = options.queryClient ?? testQueryClient(options);
  function Wrapper({ children }: { children: ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>
        <TooltipProvider>{children}</TooltipProvider>
      </QueryClientProvider>
    );
  }
  return { queryClient, ...render(ui, { wrapper: Wrapper, ...options }) };
}
