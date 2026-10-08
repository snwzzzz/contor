"use client";

import { Database, Plus } from "lucide-react";
import Link from "next/link";
import { usePathname, useSearchParams } from "next/navigation";
import { Badge } from "@/components/ui/badge";
import {
  Sidebar,
  SidebarContent,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarMenuSkeleton,
  SidebarMenuSub,
  SidebarMenuSubButton,
  SidebarMenuSubItem,
  SidebarRail,
} from "@/components/ui/sidebar";
import { useListCollections } from "@/lib/api/generated/endpoints/collections/collections";
import { useCountProposals } from "@/lib/api/generated/endpoints/proposals/proposals";
import {
  collectionHref,
  DATA_NAV,
  isActive,
  NEW_COLLECTION_HREF,
  PRIMARY_NAV,
  isSettingsSection,
  SETTINGS_NAV,
  SETTINGS_PARAM,
  SETTINGS_ROOT,
  settingsHref,
  type NavItem,
} from "@/lib/navigation";

const PROPOSAL_COUNT_REFRESH_MS = 60_000;

function NavLink({ item, pathname, badge }: { item: NavItem; pathname: string; badge?: number }) {
  const active = isActive(pathname, item.href);
  return (
    <SidebarMenuItem>
      <SidebarMenuButton asChild isActive={active} tooltip={item.title}>
        <Link href={item.href} aria-current={active ? "page" : undefined}>
          <item.icon aria-hidden="true" />
          <span>{item.title}</span>
          {badge ? (
            <Badge variant="secondary" className="ml-auto" aria-label={`${badge} pending proposals`}>
              {badge}
            </Badge>
          ) : null}
        </Link>
      </SidebarMenuButton>
    </SidebarMenuItem>
  );
}

function CollectionsGroup({ pathname }: { pathname: string }) {
  const { data: collections, isPending } = useListCollections();
  return (
    <SidebarGroup>
      <SidebarGroupLabel>Collections</SidebarGroupLabel>
      <SidebarGroupContent>
        <SidebarMenu>
          {isPending ? (
            <SidebarMenuItem>
              <SidebarMenuSkeleton showIcon />
            </SidebarMenuItem>
          ) : null}
          {collections?.map((collection) => {
            const href = collectionHref(collection.id);
            const active = isActive(pathname, href);
            return (
              <SidebarMenuItem key={collection.id}>
                <SidebarMenuButton asChild isActive={active} tooltip={collection.name}>
                  <Link href={href} aria-current={active ? "page" : undefined}>
                    <Database aria-hidden="true" />
                    <span>{collection.name}</span>
                  </Link>
                </SidebarMenuButton>
              </SidebarMenuItem>
            );
          })}
          <SidebarMenuItem>
            <SidebarMenuButton asChild tooltip="New collection">
              <Link href={NEW_COLLECTION_HREF}>
                <Plus aria-hidden="true" />
                <span>New collection</span>
              </Link>
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarGroupContent>
    </SidebarGroup>
  );
}

/** Collapsible main navigation. Collections and the pending-proposal count come from the API. */
export function AppSidebar() {
  const pathname = usePathname();
  const requestedSettings = useSearchParams().get(SETTINGS_PARAM);
  const openSettings = isSettingsSection(requestedSettings) ? requestedSettings : null;
  const { data: pending } = useCountProposals(undefined, { query: { refetchInterval: PROPOSAL_COUNT_REFRESH_MS } });

  return (
    <Sidebar collapsible="icon">
      <SidebarHeader>
        <Link href="/inbox" className="px-2 py-1 text-base font-semibold tracking-tight">
          contor
        </Link>
      </SidebarHeader>
      <SidebarContent>
        <nav aria-label="Main" className="contents">
          <SidebarGroup>
            <SidebarGroupContent>
              <SidebarMenu>
                {PRIMARY_NAV.map((item) => (
                  <NavLink key={item.href} item={item} pathname={pathname} badge={item.href === "/proposals" ? pending?.count : undefined} />
                ))}
              </SidebarMenu>
            </SidebarGroupContent>
          </SidebarGroup>
          <CollectionsGroup pathname={pathname} />
          <SidebarGroup>
            <SidebarGroupLabel>Graph & automation</SidebarGroupLabel>
            <SidebarGroupContent>
              <SidebarMenu>
                {DATA_NAV.map((item) => (
                  <NavLink key={item.href} item={item} pathname={pathname} />
                ))}
              </SidebarMenu>
            </SidebarGroupContent>
          </SidebarGroup>
          <SidebarGroup>
            <SidebarGroupContent>
              <SidebarMenu>
                <SidebarMenuItem>
                  <SidebarMenuButton asChild isActive={openSettings !== null} tooltip={SETTINGS_ROOT.title}>
                    <Link href={settingsHref(pathname, "accounts")} scroll={false}>
                      <SETTINGS_ROOT.icon aria-hidden="true" />
                      <span>{SETTINGS_ROOT.title}</span>
                    </Link>
                  </SidebarMenuButton>
                  <SidebarMenuSub>
                    {SETTINGS_NAV.map((item) => {
                      const active = openSettings === item.section;
                      return (
                        <SidebarMenuSubItem key={item.href}>
                          <SidebarMenuSubButton asChild isActive={active}>
                            <Link href={settingsHref(pathname, item.section)} scroll={false}>
                              <span>{item.title}</span>
                            </Link>
                          </SidebarMenuSubButton>
                        </SidebarMenuSubItem>
                      );
                    })}
                  </SidebarMenuSub>
                </SidebarMenuItem>
              </SidebarMenu>
            </SidebarGroupContent>
          </SidebarGroup>
        </nav>
      </SidebarContent>
      <SidebarRail />
    </Sidebar>
  );
}
