import {
  Bot,
  Brain,
  Inbox,
  KeyRound,
  ListFilter,
  Network,
  Settings,
  ShieldCheck,
  Sparkles,
  Tags,
  type LucideIcon,
} from "lucide-react";

export interface NavItem {
  title: string;
  href: string;
  icon: LucideIcon;
}

/** Top-level sections (sidebar + command palette share this list). */
export const PRIMARY_NAV: readonly NavItem[] = [
  { title: "Inbox", href: "/inbox", icon: Inbox },
  { title: "Proposals", href: "/proposals", icon: Sparkles },
];

export const DATA_NAV: readonly NavItem[] = [
  { title: "Relations", href: "/relations", icon: Network },
  { title: "Queries", href: "/queries", icon: ListFilter },
  { title: "Rules & Labels", href: "/rules", icon: Tags },
];

export const SETTINGS_NAV: readonly NavItem[] = [
  { title: "Accounts", href: "/settings/accounts", icon: KeyRound },
  { title: "AI Models", href: "/settings/ai", icon: Brain },
  { title: "Autonomy", href: "/settings/autonomy", icon: Bot },
  { title: "Security", href: "/settings/security", icon: ShieldCheck },
];

export const SETTINGS_ROOT: NavItem = { title: "Settings", href: "/settings", icon: Settings };

export const ALL_NAV: readonly NavItem[] = [...PRIMARY_NAV, ...DATA_NAV, SETTINGS_ROOT, ...SETTINGS_NAV];

export function collectionHref(collectionId: number): string {
  return `/collections/${collectionId}`;
}

export const NEW_COLLECTION_HREF = "/collections/new";

/** Exact match or a sub-page of `href`. */
export function isActive(pathname: string, href: string): boolean {
  return pathname === href || pathname.startsWith(`${href}/`);
}
