"use client";

import { Braces, Plus } from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { EmptyState } from "@/components/app/EmptyState";
import { ErrorMessage } from "@/components/app/ErrorMessage";
import { LoadingStatus } from "@/components/app/LoadingSkeletons";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { useListExternalApis } from "@/lib/api/generated/endpoints/external-apis/external-apis";
import type { ExternalApiDto } from "@/lib/api/generated/model/externalApiDto";
import { ROW_HOVER } from "@/lib/card-hover";
import { tintClass } from "@/lib/collection-tint";
import { apiHost, appTint, healthOf, intervalLabel } from "@/lib/external-apis";
import { appHref, appPageHref } from "@/lib/navigation";
import { relativeTime } from "@/lib/relative-time";
import { cn } from "@/lib/utils";

const SKELETON_ROWS = ["w-24", "w-32", "w-20"] as const;

/** Every app (external API) in one table: name and host, health, how often it is called, the last call, endpoints. */
export function AppsOverview() {
  const pathname = usePathname();
  const { data: apps, isPending, error } = useListExternalApis();

  if (error) return <ErrorMessage message={error.message} />;
  if (isPending) return <AppsOverviewSkeleton />;
  if (apps.length === 0) {
    return (
      <EmptyState
        icon={Braces}
        title="No apps yet"
        description="Connect a shop, a job platform or any other HTTPS API: contor calls it now and then, only reads, and the assistant learns from it."
        action={
          <Button asChild>
            <Link href={appHref(pathname, "new")} scroll={false}>
              <Plus aria-hidden="true" data-icon="inline-start" />
              Add app
            </Link>
          </Button>
        }
      />
    );
  }

  // Read only on the client: the list comes from the API after hydration.
  const now = new Date();
  return (
    <section aria-label="External APIs" className="grid gap-3">
      <table className="w-full table-fixed text-sm">
        <thead>
          <tr className="border-b text-left text-xs text-muted-foreground">
            <th scope="col" className="py-2 pr-4 font-normal">Name</th>
            <th scope="col" className="w-32 py-2 pr-4 font-normal">Status</th>
            <th scope="col" className="hidden w-40 py-2 pr-4 font-normal md:table-cell">How often</th>
            <th scope="col" className="hidden w-32 py-2 pr-4 font-normal md:table-cell">Last call</th>
            <th scope="col" className="w-24 py-2 text-right font-normal">Endpoints</th>
          </tr>
        </thead>
        <tbody>
          {apps.toSorted((a, b) => a.name.localeCompare(b.name)).map((api) => (
            <AppRow key={api.id} api={api} now={now} />
          ))}
        </tbody>
      </table>
    </section>
  );
}

/** One app; the whole row opens its page (the name is the link, stretched over the row). */
function AppRow({ api, now }: { api: ExternalApiDto; now: Date }) {
  const health = healthOf(api);
  return (
    <tr className={cn("relative border-b last:border-b-0 focus-within:bg-muted/50", ROW_HOVER)}>
      <td className="py-2.5 pr-4">
        <div className="flex min-w-0 items-start gap-3">
          <span
            aria-hidden="true"
            className={cn("mt-0.5 flex size-4 shrink-0 items-center justify-center rounded-sm text-[10px] font-semibold", tintClass(appTint(api.id)))}
          >
            {api.name.charAt(0).toUpperCase()}
          </span>
          <div className="grid min-w-0 gap-0.5">
            <Link
              href={appPageHref(api.id)}
              className="truncate font-medium outline-none after:absolute after:inset-0 after:content-[''] hover:underline focus-visible:underline"
            >
              {api.name}
            </Link>
            <span className="truncate text-xs text-muted-foreground">{apiHost(api.baseUrl)}</span>
          </div>
        </div>
      </td>
      <td className="py-2.5 pr-4">
        <span className="flex items-center gap-1.5">
          <span aria-hidden="true" className={cn("size-1.5 shrink-0 rounded-full", health.dot)} />
          {health.label}
        </span>
      </td>
      <td className="hidden py-2.5 pr-4 text-muted-foreground md:table-cell">{intervalLabel(api.pollIntervalMinutes)}</td>
      <td className="hidden py-2.5 pr-4 text-muted-foreground tabular-nums md:table-cell">
        {api.lastCalledAt ? relativeTime(api.lastCalledAt, now) : "Not called yet"}
      </td>
      <td className="py-2.5 text-right tabular-nums">{api.endpointCount}</td>
    </tr>
  );
}

/** The apps table while it loads: rows at the real heights. */
export function AppsOverviewSkeleton() {
  return (
    <LoadingStatus label="Loading apps" className="grid gap-3">
      <table aria-hidden="true" className="w-full table-fixed text-sm">
        <thead>
          <tr className="border-b">
            <th className="py-2 pr-4"><Skeleton className="h-3 w-12" /></th>
            <th className="w-32 py-2 pr-4"><Skeleton className="h-3 w-12" /></th>
            <th className="hidden w-40 py-2 pr-4 md:table-cell"><Skeleton className="h-3 w-16" /></th>
            <th className="hidden w-32 py-2 pr-4 md:table-cell"><Skeleton className="h-3 w-14" /></th>
            <th className="w-24 py-2"><Skeleton className="ml-auto h-3 w-16" /></th>
          </tr>
        </thead>
        <tbody>
          {SKELETON_ROWS.map((width) => (
            <tr key={width} className="border-b last:border-b-0">
              <td className="py-2.5 pr-4">
                <div className="flex items-start gap-3">
                  <Skeleton className="mt-0.5 size-4 shrink-0" />
                  <div className="grid flex-1 gap-1.5 py-0.5">
                    <Skeleton className={cn("h-3.5", width)} />
                    <Skeleton className="h-3 w-28" />
                  </div>
                </div>
              </td>
              <td className="py-2.5 pr-4"><Skeleton className="h-3.5 w-16" /></td>
              <td className="hidden py-2.5 pr-4 md:table-cell"><Skeleton className="h-3.5 w-24" /></td>
              <td className="hidden py-2.5 pr-4 md:table-cell"><Skeleton className="h-3.5 w-16" /></td>
              <td className="py-2.5"><Skeleton className="ml-auto h-3.5 w-4" /></td>
            </tr>
          ))}
        </tbody>
      </table>
    </LoadingStatus>
  );
}
