import { MutationCache, QueryCache, QueryClient } from "@tanstack/react-query";
import { ApiError } from "./fetcher";

/** Per-query/mutation flags (typed via the Register declaration below). */
export interface RequestMeta extends Record<string, unknown> {
  /** A 401 is an expected answer (e.g. login, setup status); do not send the user to the login page. */
  skipAuthRedirect?: boolean;
  /** The component shows the error itself; do not toast it. */
  silent?: boolean;
}

declare module "@tanstack/react-query" {
  interface Register {
    queryMeta: RequestMeta;
    mutationMeta: RequestMeta;
  }
}

export interface QueryClientOptions {
  /** Called when the session is gone (any 401 not marked skipAuthRedirect). */
  onUnauthorized: () => void;
  /** Shows a failed mutation's message (the app uses a toast). */
  notifyError: (message: string) => void;
  /** Tests disable retries. */
  retry?: boolean;
}

const GENERIC_ERROR = "Something went wrong. Please try again.";

function isUnauthorized(error: unknown) {
  return error instanceof ApiError && error.status === 401;
}

export function createQueryClient({ onUnauthorized, notifyError, retry = true }: QueryClientOptions) {
  return new QueryClient({
    queryCache: new QueryCache({
      onError: (error, query) => {
        if (isUnauthorized(error) && !query.meta?.skipAuthRedirect) onUnauthorized();
      },
    }),
    mutationCache: new MutationCache({
      onError: (error, _variables, _context, mutation) => {
        if (isUnauthorized(error) && !mutation.meta?.skipAuthRedirect) {
          onUnauthorized();
          return;
        }
        if (!mutation.meta?.silent) notifyError(error instanceof ApiError ? error.message : GENERIC_ERROR);
      },
    }),
    defaultOptions: {
      queries: {
        staleTime: 30_000,
        // Client errors (4xx) will not fix themselves; retry only network/server problems.
        retry: retry ? (count, error) => !(error instanceof ApiError && error.status < 500) && count < 2 : false,
      },
      mutations: { retry: false },
    },
  });
}
