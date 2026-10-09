"use client";

import { useQueryClient } from "@tanstack/react-query";
import { CheckCircle2, CircleAlert, Download } from "lucide-react";
import { AppButton } from "@/components/app/AppButton";
import { ErrorMessage } from "@/components/app/ErrorMessage";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { getGetAiStatusQueryKey, useGetAiStatus, usePullAiModels } from "@/lib/api/generated/endpoints/ai/ai";
import type { AiModelDto } from "@/lib/api/generated/model/aiModelDto";
import type { AiStatusDto } from "@/lib/api/generated/model/aiStatusDto";

const PULL_POLL_MS = 1500;
const ROLE_LABEL = { EXTRACTION: "Extraction", ROUTING: "Routing", EMBEDDING: "Embeddings" } as const;

function isBusy(status: AiStatusDto | undefined): boolean {
  return (status?.models.some((model) => model.pullState === "PULLING") || status?.embeddings?.running) ?? false;
}

/** Whether the local Ollama runs and the configured models are installed; downloads missing ones. */
export function AiModelStatus() {
  const queryClient = useQueryClient();
  const { data: status, isLoading, error, refetch, isFetching } = useGetAiStatus({
    query: { refetchInterval: (query) => (isBusy(query.state.data) ? PULL_POLL_MS : false) },
  });
  const pull = usePullAiModels({
    mutation: { onSuccess: (data) => queryClient.setQueryData(getGetAiStatusQueryKey(), data) },
  });

  if (isLoading) {
    return <p className="text-sm text-muted-foreground">Checking Ollama…</p>;
  }
  if (error || !status) {
    return <ErrorMessage message={error?.message ?? "Could not check the AI status."} />;
  }
  if (!status.reachable) {
    return (
      <div className="grid gap-3">
        <Alert role="status">
          <CircleAlert aria-hidden="true" />
          <AlertDescription>
            Ollama is not running. Start Ollama on this computer (it listens on 127.0.0.1:11434), then check again.
          </AlertDescription>
        </Alert>
        <AppButton variant="outline" className="w-fit" loading={isFetching} onClick={() => refetch()}>
          Check again
        </AppButton>
      </div>
    );
  }

  const canDownload = status.models.some((model) => !model.installed && model.pullState !== "PULLING");
  return (
    <div className="grid gap-4">
      <p className="text-sm text-muted-foreground">Ollama {status.version}</p>
      <ul className="grid gap-2">
        {status.models.map((model) => (
          <li key={model.name} className="flex flex-wrap items-center justify-between gap-2 rounded-lg border px-4 py-3">
            <div className="grid gap-0.5">
              <span className="font-mono text-sm">{model.name}</span>
              <span className="text-xs text-muted-foreground">{model.roles.map((role) => ROLE_LABEL[role]).join(", ")}</span>
            </div>
            <ModelState model={model} />
          </li>
        ))}
      </ul>
      {status.embeddings && status.embeddings.total > 0 ? <SearchIndex indexed={status.embeddings.indexed} total={status.embeddings.total} running={status.embeddings.running} /> : null}
      {status.ready ? (
        <p className="flex items-center gap-2 text-sm">
          <CheckCircle2 className="size-4 text-primary" aria-hidden="true" />
          Everything is ready.
        </p>
      ) : null}
      {canDownload ? (
        <AppButton className="w-fit" loading={pull.isPending} onClick={() => pull.mutate()}>
          <Download aria-hidden="true" data-icon="inline-start" />
          Download missing models
        </AppButton>
      ) : null}
    </div>
  );
}

/** How much of the inbox the semantic search can already find. */
function SearchIndex({ indexed, total, running }: { indexed: number; total: number; running: boolean }) {
  return (
    <div className="grid gap-1.5 rounded-lg border px-4 py-3">
      <div className="flex items-center justify-between gap-2 text-sm">
        <span>{`Search index: ${indexed} of ${total} emails`}</span>
        {running ? <span className="text-xs text-muted-foreground">Indexing…</span> : null}
      </div>
      <div
        role="progressbar"
        aria-label="Search index"
        aria-valuemin={0}
        aria-valuemax={total}
        aria-valuenow={indexed}
        className="h-1.5 overflow-hidden rounded-full bg-muted"
      >
        <div className="h-full bg-primary transition-[width]" style={{ width: `${Math.min(100, Math.round((indexed / total) * 100))}%` }} />
      </div>
    </div>
  );
}

function ModelState({ model }: { model: AiModelDto }) {
  if (model.installed) {
    return <Badge className="bg-success/15 text-success">Installed</Badge>;
  }
  if (model.pullState === "PULLING") {
    return (
      <div className="grid w-40 gap-1">
        <span className="text-xs text-muted-foreground">Downloading {model.pullPercent ?? 0}%</span>
        <div className="h-1.5 overflow-hidden rounded-full bg-muted" aria-hidden="true">
          <div className="h-full bg-primary transition-[width]" style={{ width: `${model.pullPercent ?? 0}%` }} />
        </div>
      </div>
    );
  }
  if (model.pullState === "FAILED") {
    return <Badge variant="destructive">Download failed: {model.pullError}</Badge>;
  }
  return <Badge variant="outline">Not installed</Badge>;
}
