"use client";

import { useQueryClient } from "@tanstack/react-query";
import { Braces, Database } from "lucide-react";
import Link from "next/link";
import { toast } from "sonner";
import { AppButton } from "@/components/app/AppButton";
import { DetailSheet } from "@/components/app/DetailSheet";
import { ErrorMessage } from "@/components/app/ErrorMessage";
import { LoadingList } from "@/components/app/LoadingSkeletons";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  getGetExternalApiEndpointDataQueryKey,
  getGetExternalApiQueryKey,
  getListExternalApiEndpointsQueryKey,
  useCallExternalApiNow,
  useGetExternalApiEndpointData,
  useUpdateExternalApiEndpoint,
} from "@/lib/api/generated/endpoints/external-apis/external-apis";
import type { ExternalApiAnswerDto } from "@/lib/api/generated/model/externalApiAnswerDto";
import type { ExternalApiDto } from "@/lib/api/generated/model/externalApiDto";
import type { ExternalApiEndpointDto } from "@/lib/api/generated/model/externalApiEndpointDto";
import type { ExternalApiItemDto } from "@/lib/api/generated/model/externalApiItemDto";
import { appTint, detectItems, prettyBody } from "@/lib/external-apis";
import { relativeTime } from "@/lib/relative-time";

interface EndpointDataProps {
  api: ExternalApiDto;
  endpoint: ExternalApiEndpointDto;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

/** What the item is called: its title or name, else its key. */
function itemTitle(item: ExternalApiItemDto): string {
  const named = item.data.title ?? item.data.name;
  return typeof named === "string" && named.trim() ? named : item.key;
}

/**
 * What an endpoint brought in, in the right sidebar: the items read from its answers (with the record each became)
 * and the last answer as it came. When the items path reads nothing, the answer's list is offered as the fix.
 */
export function EndpointData({ api, endpoint, open, onOpenChange }: EndpointDataProps) {
  const { data, isPending, error } = useGetExternalApiEndpointData(api.id, endpoint.id, { query: { enabled: open } });
  // Read only on the client: the sheet opens after a click.
  const now = new Date();

  return (
    <DetailSheet open={open} onOpenChange={onOpenChange} title={endpoint.name} label={`${api.name} · ${endpoint.method} ${endpoint.path}`}
                 icon={Braces} tint={appTint(api.id)}>
      {error ? <ErrorMessage message={error.message} /> : null}
      {isPending ? <LoadingList label="Loading the data" rows={6} twoLines /> : null}
      {data ? (
        !data.answer ? (
          <p className="text-sm text-muted-foreground">No answer yet. The first call comes with the next poll.</p>
        ) : (
          <div className="grid gap-4">
            <p className="text-sm text-muted-foreground">
              {data.itemCount} {data.itemCount === 1 ? "item" : "items"} · {data.goneCount} gone · last answer {relativeTime(data.answer.fetchedAt, now)}
            </p>
            {data.itemCount === 0 ? <NoItems api={api} endpoint={endpoint} answer={data.answer} /> : null}
            <Tabs defaultValue="items" className="grid gap-3">
              <TabsList variant="line" className="justify-start">
                <TabsTrigger value="items" className="flex-none text-xs">Items</TabsTrigger>
                <TabsTrigger value="answer" className="flex-none text-xs">Last answer</TabsTrigger>
              </TabsList>
              <TabsContent value="items">
                {data.items.length === 0 ? (
                  <p className="text-sm text-muted-foreground">No items.</p>
                ) : (
                  <ul className="-mx-2 grid">
                    {data.items.map((item) => (
                      <ItemRow key={item.id} item={item} />
                    ))}
                  </ul>
                )}
                {data.itemCount > data.items.length ? (
                  <p className="mt-2 text-xs text-muted-foreground">The newest {data.items.length} of {data.itemCount} are shown.</p>
                ) : null}
              </TabsContent>
              <TabsContent value="answer" className="grid gap-2">
                <p className="text-xs text-muted-foreground">
                  {data.answer.contentType ?? "No content type"}
                  {data.answer.truncated ? " · only the first part" : ""}
                </p>
                <pre className="overflow-auto rounded-md border px-3 py-2 font-mono text-xs whitespace-pre-wrap break-all">
                  {data.answer.truncated ? data.answer.body : prettyBody(data.answer.body)}
                </pre>
              </TabsContent>
            </Tabs>
          </div>
        )
      ) : null}
    </DetailSheet>
  );
}

function ItemRow({ item }: { item: ExternalApiItemDto }) {
  const title = itemTitle(item);
  return (
    <li aria-label={title} className="grid gap-1 rounded-md px-2 py-2">
      <span className="flex min-w-0 items-baseline gap-2">
        <span className="truncate text-sm font-medium">{title}</span>
        <span className="shrink-0 font-mono text-xs text-muted-foreground">{item.key}</span>
        {item.collectionId !== undefined && item.recordId !== undefined ? (
          <Link href={`/collections/${item.collectionId}/records/${item.recordId}`}
                className="ml-auto inline-flex shrink-0 items-center gap-1 text-xs underline underline-offset-2">
            <Database aria-hidden="true" className="size-3" />
            Open record
          </Link>
        ) : null}
      </span>
      <details>
        <summary className="cursor-pointer text-xs text-muted-foreground">Data</summary>
        <pre className="mt-1 max-h-64 overflow-auto rounded-md border px-2 py-1 font-mono text-xs whitespace-pre-wrap break-all">
          {JSON.stringify(item.data, null, 2)}
        </pre>
      </details>
    </li>
  );
}

/** Nothing was read from a real answer: say why, and offer the list the answer has. */
function NoItems({ api, endpoint, answer }: { api: ExternalApiDto; endpoint: ExternalApiEndpointDto; answer: ExternalApiAnswerDto }) {
  const queryClient = useQueryClient();
  const callNow = useCallExternalApiNow();
  const update = useUpdateExternalApiEndpoint({
    mutation: {
      onSuccess: async () => {
        // The changed path makes the next answer count as new: call now to read the items.
        await callNow.mutateAsync({ apiId: api.id });
        await Promise.all([
          queryClient.invalidateQueries({ queryKey: getGetExternalApiEndpointDataQueryKey(api.id, endpoint.id) }),
          queryClient.invalidateQueries({ queryKey: getListExternalApiEndpointsQueryKey(api.id) }),
          queryClient.invalidateQueries({ queryKey: getGetExternalApiQueryKey(api.id) }),
        ]);
        toast.success("Saved. The items are read within a minute.");
      },
    },
  });
  const detected = answer.truncated ? null : detectItems(answer.body);
  const suggested = detected && detected.itemsPath !== endpoint.itemsPath ? detected : null;
  return (
    <div className="grid gap-2 rounded-lg border p-3 text-sm">
      <p>
        {endpoint.itemsPath
          ? `No items were read: the items path ${endpoint.itemsPath} finds nothing in the answer.`
          : "No items were read from the answer."}
      </p>
      {suggested ? (
        <>
          <p className="text-muted-foreground">
            The answer has {suggested.count} {suggested.count === 1 ? "item" : "items"} at {suggested.itemsPath}
            {suggested.itemKeyPath ? `, identified by ${suggested.itemKeyPath}` : ""}.
          </p>
          <AppButton size="sm" className="w-fit" loading={update.isPending || callNow.isPending}
                     onClick={() => update.mutate({ apiId: api.id, endpointId: endpoint.id,
                       data: { itemsPath: suggested.itemsPath, ...(suggested.itemKeyPath ? { itemKeyPath: suggested.itemKeyPath } : {}) } })}>
            Use these paths
          </AppButton>
        </>
      ) : null}
    </div>
  );
}
