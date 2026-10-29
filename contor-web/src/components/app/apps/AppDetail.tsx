"use client";

import { useQueryClient } from "@tanstack/react-query";
import { Pause, Pencil, Play, Plug, Zap } from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { toast } from "sonner";
import { AppButton } from "@/components/app/AppButton";
import { EmptyState } from "@/components/app/EmptyState";
import { ErrorMessage } from "@/components/app/ErrorMessage";
import { HomeSection, QuietEmpty } from "@/components/app/home/HomeSection";
import { LoadingStatus, PageHeaderSkeleton } from "@/components/app/LoadingSkeletons";
import { RefreshCommand } from "@/components/app/PageCommandButtons";
import { COMMAND, COMMAND_LABEL, CommandLink, PageCommands } from "@/components/app/PageCommands";
import { PageHeader, type Crumb } from "@/components/app/PageHeader";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { useListCollections } from "@/lib/api/generated/endpoints/collections/collections";
import {
  getGetExternalApiQueryKey,
  getListExternalApisQueryKey,
  useCallExternalApiNow,
  useGetExternalApi,
  useListExternalApiCalls,
  useListExternalApiEndpoints,
  useUpdateExternalApi,
} from "@/lib/api/generated/endpoints/external-apis/external-apis";
import type { ExternalApiCallDto } from "@/lib/api/generated/model/externalApiCallDto";
import type { ExternalApiDto } from "@/lib/api/generated/model/externalApiDto";
import type { ExternalApiEndpointDto } from "@/lib/api/generated/model/externalApiEndpointDto";
import { tintClass } from "@/lib/collection-tint";
import { apiHost, appTint, healthOf, intervalLabel } from "@/lib/external-apis";
import { APPS_HREF, appHref, collectionHref } from "@/lib/navigation";
import { relativeTime } from "@/lib/relative-time";
import { cn } from "@/lib/utils";

const APPS_CRUMB: Crumb = { label: "Apps", href: APPS_HREF };

const OUTCOME: Record<ExternalApiCallDto["outcome"], { label: string; dot: string }> = {
  OK: { label: "New answer", dot: "bg-success" },
  UNCHANGED: { label: "Unchanged", dot: "bg-muted-foreground/40" },
  FAILED: { label: "Failed", dot: "bg-destructive" },
};

/**
 * One app (external API): its health and schedule, its endpoints with where their items go, and its recent calls;
 * Call now, Pause/Resume and Edit (the modal over this page).
 */
export function AppDetail({ apiId }: { apiId: number }) {
  const { data: api, isPending, error } = useGetExternalApi(apiId, { query: { retry: false } });

  if (isPending) return <AppDetailSkeleton />;
  if (error || !api) {
    return (
      <EmptyState
        icon={Plug}
        title={error?.status === 404 ? "This app does not exist (any more)." : "The app could not be loaded."}
        action={
          <Button asChild variant="outline">
            <Link href={APPS_HREF}>All apps</Link>
          </Button>
        }
      />
    );
  }
  return <AppPage api={api} />;
}

function AppPage({ api }: { api: ExternalApiDto }) {
  const pathname = usePathname();
  const health = healthOf(api);
  // The page shows data loaded after hydration only: the clock is read on the client.
  const now = new Date();
  return (
    <div className="grid gap-8">
      <PageHeader
        trail={[APPS_CRUMB]}
        title={api.name}
        subtitle={apiHost(api.baseUrl)}
        description={api.description}
        media={
          <span
            aria-hidden="true"
            className={cn("flex size-8 shrink-0 items-center justify-center rounded-md text-sm font-semibold", tintClass(appTint(api.id)))}
          >
            {api.name.charAt(0).toUpperCase()}
          </span>
        }
        actions={
          <PageCommands>
            <RefreshCommand />
            <CallNowCommand api={api} />
            <PauseCommand api={api} />
            <CommandLink href={appHref(pathname, api.id)} icon={Pencil} label="Edit" overlay />
          </PageCommands>
        }
      />
      <div className="grid grid-cols-[minmax(0,1fr)] items-start gap-x-10 gap-y-8 lg:grid-cols-[repeat(3,minmax(0,1fr))]">
        <HomeSection title="Status">
          <dl className="grid grid-cols-[auto_minmax(0,1fr)] gap-x-6 gap-y-2 text-sm">
            <dt className="text-muted-foreground">Status</dt>
            <dd className="flex items-center gap-1.5">
              <span aria-hidden="true" className={cn("size-1.5 shrink-0 rounded-full", health.dot)} />
              {health.label}
              {api.failureCount > 0 ? <span className="text-muted-foreground"> · {api.failureCount} failed in a row</span> : null}
            </dd>
            <dt className="text-muted-foreground">How often</dt>
            <dd>{intervalLabel(api.pollIntervalMinutes)}</dd>
            <dt className="text-muted-foreground">Last call</dt>
            <dd className="tabular-nums">{api.lastCalledAt ? relativeTime(api.lastCalledAt, now) : "Not called yet"}</dd>
            <dt className="text-muted-foreground">Next call</dt>
            <dd className="tabular-nums">{api.enabled && api.nextCallAt ? relativeTime(api.nextCallAt, now) : "–"}</dd>
            {api.lastError ? (
              <>
                <dt className="text-muted-foreground">Last error</dt>
                <dd className="text-destructive">{api.lastError}</dd>
              </>
            ) : null}
          </dl>
        </HomeSection>
        <div className="lg:col-span-2">
          <Endpoints apiId={api.id} />
        </div>
      </div>
      <CallHistory apiId={api.id} now={now} />
    </div>
  );
}

function CallNowCommand({ api }: { api: ExternalApiDto }) {
  const queryClient = useQueryClient();
  const callNow = useCallExternalApiNow({
    mutation: {
      onSuccess: (saved) => {
        queryClient.setQueryData(getGetExternalApiQueryKey(api.id), saved);
        toast.success(`${api.name} is called within a minute`);
      },
    },
  });
  return (
    <AppButton variant="ghost" size="sm" className={COMMAND} loading={callNow.isPending} disabled={!api.enabled}
               onClick={() => callNow.mutate({ apiId: api.id })}>
      {callNow.isPending ? null : <Zap aria-hidden="true" data-icon="inline-start" />}
      <span className={COMMAND_LABEL}>Call now</span>
    </AppButton>
  );
}

function PauseCommand({ api }: { api: ExternalApiDto }) {
  const queryClient = useQueryClient();
  const update = useUpdateExternalApi({
    mutation: {
      onSuccess: async (saved) => {
        queryClient.setQueryData(getGetExternalApiQueryKey(api.id), saved);
        await queryClient.invalidateQueries({ queryKey: getListExternalApisQueryKey() });
      },
    },
  });
  const Icon = api.enabled ? Pause : Play;
  return (
    <AppButton variant="ghost" size="sm" className={COMMAND} loading={update.isPending}
               onClick={() => update.mutate({ apiId: api.id, data: { enabled: !api.enabled } })}>
      {update.isPending ? null : <Icon aria-hidden="true" data-icon="inline-start" />}
      <span className={COMMAND_LABEL}>{api.enabled ? "Pause" : "Resume"}</span>
    </AppButton>
  );
}

function Endpoints({ apiId }: { apiId: number }) {
  const { data: endpoints, error } = useListExternalApiEndpoints(apiId);
  const { data: collections } = useListCollections();
  const names = new Map(collections?.map((collection) => [collection.id, collection.name]));
  return (
    <HomeSection title="Endpoints">
      {error ? <ErrorMessage message={error.message} /> : null}
      {endpoints && endpoints.length === 0 ? (
        <QuietEmpty icon={Plug} title="No endpoints yet" description="Edit the app to add what contor should call." />
      ) : null}
      {endpoints && endpoints.length > 0 ? (
        <ul className="-mx-2 grid">
          {endpoints.map((endpoint) => (
            <EndpointRow key={endpoint.id} endpoint={endpoint} collectionName={endpoint.mapping ? names.get(endpoint.mapping.collectionId) : undefined} />
          ))}
        </ul>
      ) : null}
    </HomeSection>
  );
}

function EndpointRow({ endpoint, collectionName }: { endpoint: ExternalApiEndpointDto; collectionName: string | undefined }) {
  const mapping = endpoint.mapping;
  return (
    <li aria-label={endpoint.name} className="grid gap-0.5 rounded-md px-2 py-2">
      <span className="flex min-w-0 items-baseline gap-2">
        <span className="truncate text-sm font-medium">{endpoint.name}</span>
        {endpoint.enabled ? null : <span className="text-xs text-muted-foreground">off</span>}
      </span>
      <span className="truncate font-mono text-xs text-muted-foreground">
        <span className="mr-1.5">{endpoint.method}</span>
        {endpoint.path}
      </span>
      <span className="text-xs text-muted-foreground">
        {!mapping ? (
          "Not connected to a collection yet"
        ) : mapping.state === "PROPOSED" ? (
          <>
            Waiting for your approval ·{" "}
            <Link href="/proposals" className="text-foreground underline underline-offset-2">
              Review
            </Link>
          </>
        ) : (
          <>
            {mapping.state === "OFF" ? "Was into " : "Into "}
            <Link href={collectionHref(mapping.collectionId)} className="text-foreground underline underline-offset-2">
              {collectionName ?? "a collection"}
            </Link>
            {" · "}
            {mapping.fieldCount} {mapping.fieldCount === 1 ? "field" : "fields"}
          </>
        )}
      </span>
    </li>
  );
}

function CallHistory({ apiId, now }: { apiId: number; now: Date }) {
  const { data: calls, error } = useListExternalApiCalls(apiId);
  return (
    <HomeSection title="Call history">
      {error ? <ErrorMessage message={error.message} /> : null}
      {calls && calls.length === 0 ? <QuietEmpty icon={Zap} title="No calls yet" description="The first call comes with the next poll." /> : null}
      {calls && calls.length > 0 ? (
        <table className="w-full table-fixed text-sm">
          <thead>
            <tr className="border-b text-left text-xs text-muted-foreground">
              <th scope="col" className="w-28 py-2 pr-4 font-normal">When</th>
              <th scope="col" className="py-2 pr-4 font-normal">Endpoint</th>
              <th scope="col" className="w-32 py-2 pr-4 font-normal">Result</th>
              <th scope="col" className="hidden w-16 py-2 pr-4 text-right font-normal md:table-cell">Status</th>
              <th scope="col" className="hidden w-28 py-2 text-right font-normal md:table-cell">Items</th>
            </tr>
          </thead>
          <tbody>
            {calls.map((call) => {
              const outcome = OUTCOME[call.outcome];
              return (
                <tr key={call.id} className="border-b align-top last:border-b-0">
                  <td className="py-2 pr-4 text-muted-foreground tabular-nums">{relativeTime(call.startedAt, now)}</td>
                  <td className="py-2 pr-4">
                    <span className="block truncate">{call.endpointName}</span>
                    {call.error ? <span className="block text-xs text-muted-foreground">{call.error}</span> : null}
                  </td>
                  <td className="py-2 pr-4">
                    <span className="flex items-center gap-1.5">
                      <span aria-hidden="true" className={cn("size-1.5 shrink-0 rounded-full", outcome.dot)} />
                      {outcome.label}
                    </span>
                  </td>
                  <td className="hidden py-2 pr-4 text-right tabular-nums md:table-cell">{call.httpStatus ?? "–"}</td>
                  <td className="hidden py-2 text-right text-muted-foreground tabular-nums md:table-cell">
                    {call.outcome === "OK" ? `+${call.itemsNew} ~${call.itemsChanged} −${call.itemsGone}` : ""}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      ) : null}
    </HomeSection>
  );
}

/** The app page while it loads: header, status, endpoints and history in their shapes. */
export function AppDetailSkeleton() {
  return (
    <LoadingStatus label="Loading app" className="grid gap-8">
      <PageHeaderSkeleton commands={4} />
      <div className="grid grid-cols-[minmax(0,1fr)] gap-x-10 gap-y-8 lg:grid-cols-[repeat(3,minmax(0,1fr))]" aria-hidden="true">
        <div className="grid gap-2">
          <Skeleton className="h-8 w-full" />
          {["w-24", "w-32", "w-20", "w-28"].map((width) => (
            <Skeleton key={width} className={cn("h-4", width)} />
          ))}
        </div>
        <div className="grid gap-2 lg:col-span-2">
          <Skeleton className="h-8 w-full" />
          {["w-40", "w-32", "w-36"].map((width) => (
            <div key={width} className="grid gap-1 py-2">
              <Skeleton className={cn("h-4", width)} />
              <Skeleton className="h-3 w-24" />
            </div>
          ))}
        </div>
      </div>
      <div className="grid gap-2" aria-hidden="true">
        <Skeleton className="h-8 w-full" />
        {["w-full", "w-full", "w-full"].map((width, index) => (
          <Skeleton key={index} className={cn("h-6", width)} />
        ))}
      </div>
    </LoadingStatus>
  );
}
