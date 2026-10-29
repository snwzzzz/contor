"use client";

import { BookOpen } from "lucide-react";
import { useState } from "react";
import { AnimatedDots } from "@/components/app/AnimatedDots";
import { AppButton } from "@/components/app/AppButton";
import { ErrorMessage } from "@/components/app/ErrorMessage";
import { useGetApiDiscovery, useStartApiDiscovery } from "@/lib/api/generated/endpoints/external-apis/external-apis";
import type { ApiDiscoveryDto } from "@/lib/api/generated/model/apiDiscoveryDto";
import { discoveredDraft, type EndpointDraft } from "@/lib/external-apis";

/** How often a running discovery (the model reads the docs) is asked for. */
export const DISCOVERY_POLL_MS = 2000;

interface DocsDiscoveryProps {
  docsUrl: string;
  baseUrl: string;
  /** The saved app: its endpoints are not suggested again. */
  apiId?: number;
  onAdd: (endpoints: EndpointDraft[]) => void | Promise<void>;
}

/**
 * "Read the docs": contor fetches the documentation link and suggests GET endpoints (an OpenAPI document is read in
 * code, other docs by the assistant in the background). The user picks the ones to add; nothing is saved here.
 */
export function DocsDiscovery({ docsUrl, baseUrl, apiId, onAdd }: DocsDiscoveryProps) {
  const [started, setStarted] = useState<ApiDiscoveryDto | null>(null);
  const [unchecked, setUnchecked] = useState<ReadonlySet<number>>(new Set());
  const [adding, setAdding] = useState(false);
  const start = useStartApiDiscovery({ mutation: { meta: { silent: true } } });
  const running = started?.state === "RUNNING";
  const polled = useGetApiDiscovery(started?.id ?? "", {
    query: { enabled: running, refetchInterval: (query) => (query.state.data?.state === "RUNNING" ? DISCOVERY_POLL_MS : false) },
  });
  const discovery = running ? (polled.data ?? started) : started;

  function read() {
    setUnchecked(new Set());
    start.mutate(
      { data: { docsUrl: docsUrl.trim(), baseUrl: baseUrl.trim(), ...(apiId !== undefined ? { apiId } : {}) } },
      { onSuccess: setStarted },
    );
  }

  async function add() {
    if (!discovery) return;
    setAdding(true);
    try {
      await onAdd(discovery.endpoints.filter((_, index) => !unchecked.has(index)).map(discoveredDraft));
      setStarted(null);
    } finally {
      setAdding(false);
    }
  }

  function toggle(index: number) {
    setUnchecked((current) => {
      const next = new Set(current);
      if (!next.delete(index)) next.add(index);
      return next;
    });
  }

  const chosen = discovery ? discovery.endpoints.length - unchecked.size : 0;
  const hasDocs = docsUrl.trim() !== "";

  return (
    <section aria-label="Read the docs" className="grid gap-3">
      <div className="flex flex-wrap items-center gap-3">
        <AppButton variant="outline" size="sm" disabled={!hasDocs || discovery?.state === "RUNNING"} loading={start.isPending} onClick={read}>
          <BookOpen aria-hidden="true" data-icon="inline-start" />
          Read the docs
        </AppButton>
        {!hasDocs ? <span className="text-sm text-muted-foreground">Add a documentation link under Basics to read it.</span> : null}
      </div>
      <ErrorMessage message={start.error?.message} />
      {discovery?.state === "RUNNING" ? (
        <p role="status" className="text-sm text-muted-foreground">
          The assistant is reading the docs
          <AnimatedDots />
        </p>
      ) : null}
      {discovery?.state === "FAILED" ? (
        <p role="status" className="text-sm text-destructive">
          {discovery.error}
        </p>
      ) : null}
      {discovery?.state === "DONE" ? (
        discovery.endpoints.length === 0 ? (
          <p role="status" className="text-sm text-muted-foreground">
            The docs suggest no endpoints this app does not have yet.
          </p>
        ) : (
          <div className="grid gap-3 rounded-lg border p-4">
            <p className="text-sm text-muted-foreground">
              {discovery.source === "MODEL" ? "Read by the assistant. Check the paths before adding them." : "Read from the OpenAPI document."}
            </p>
            <ul aria-label="Suggested endpoints" className="grid gap-2">
              {discovery.endpoints.map((endpoint, index) => (
                <li key={`${endpoint.path}?${JSON.stringify(endpoint.query)}`}>
                  <label className="flex items-start gap-3">
                    <input
                      type="checkbox"
                      className="mt-0.5 size-4 accent-primary"
                      checked={!unchecked.has(index)}
                      onChange={() => toggle(index)}
                    />
                    <span className="grid min-w-0 gap-0.5">
                      <span className="text-sm font-medium">{endpoint.name}</span>
                      <span className="truncate font-mono text-xs text-muted-foreground">
                        <span className="mr-1.5">{endpoint.method}</span>
                        {endpoint.path}
                        {Object.keys(endpoint.query).length > 0 ? `?${new URLSearchParams(endpoint.query).toString()}` : ""}
                      </span>
                      {endpoint.description ? <span className="text-xs text-muted-foreground">{endpoint.description}</span> : null}
                    </span>
                  </label>
                </li>
              ))}
            </ul>
            <div className="flex justify-end gap-2">
              <AppButton variant="ghost" size="sm" onClick={() => setStarted(null)}>
                Discard
              </AppButton>
              <AppButton size="sm" disabled={chosen === 0} loading={adding} onClick={add}>
                Add {chosen} {chosen === 1 ? "endpoint" : "endpoints"}
              </AppButton>
            </div>
          </div>
        )
      ) : null}
    </section>
  );
}
