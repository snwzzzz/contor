import { useGetLatestAnalysis } from "@/lib/api/generated/endpoints/ai/ai";
import type { AnalysisRunDto } from "@/lib/api/generated/model/analysisRunDto";
import type { AnalysisState } from "@/lib/api/generated/model/analysisState";

const ACTIVE_STATES: ReadonlySet<AnalysisState> = new Set(["QUEUED", "DISCOVERING", "AWAITING_REVIEW", "EXTRACTING"]);

/** Poll interval while a run is active (the worker updates its counters after every email). */
const POLL_MS = 2000;

export function isActiveRun(run: AnalysisRunDto | undefined): boolean {
  return run !== undefined && ACTIVE_STATES.has(run.status);
}

/**
 * The current user's latest analysis run, polled while it is active. "No run yet" is a 404,
 * so the error is expected and simply means `run` is undefined.
 */
export function useLatestAnalysis() {
  const query = useGetLatestAnalysis({
    query: {
      retry: false,
      refetchInterval: (current) => (isActiveRun(current.state.data) ? POLL_MS : false),
    },
  });
  return { run: query.error ? undefined : query.data, isLoading: query.isLoading, queryKey: query.queryKey };
}
