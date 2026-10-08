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

/** Settings open as a modal over the current page, addressed by `?settings=<section>`. */
export const SETTINGS_PARAM = "settings";

export const SETTINGS_SECTIONS = ["accounts", "ai", "autonomy", "security"] as const;
export type SettingsSection = (typeof SETTINGS_SECTIONS)[number];

export interface SettingsNavItem extends NavItem {
  section: SettingsSection;
  description: string;
}

/** `href` (/settings/<section>) redirects into the modal; in-app links use {@link settingsHref}. */
export const SETTINGS_NAV: readonly SettingsNavItem[] = [
  { title: "Accounts", href: "/settings/accounts", section: "accounts", icon: KeyRound, description: "Connected mailboxes." },
  { title: "AI Models", href: "/settings/ai", section: "ai", icon: Brain, description: "Local Ollama models used for routing, extraction and embeddings." },
  { title: "Autonomy", href: "/settings/autonomy", section: "autonomy", icon: Bot, description: "How much the AI may do without asking." },
  { title: "Security", href: "/settings/security", section: "security", icon: ShieldCheck, description: "Password and two-factor authentication." },
];

export const SETTINGS_ROOT: NavItem = { title: "Settings", href: "/settings", icon: Settings };

export function isSettingsSection(value: string | null | undefined): value is SettingsSection {
  return SETTINGS_SECTIONS.some((section) => section === value);
}

/** The current page with the settings modal opened at `section`. */
export function settingsHref(pathname: string, section: SettingsSection): string {
  return `${pathname}?${SETTINGS_PARAM}=${section}`;
}

export const ALL_NAV: readonly NavItem[] = [...PRIMARY_NAV, ...DATA_NAV, SETTINGS_ROOT, ...SETTINGS_NAV];

/** Where an in-app link to `item` goes: settings entries open the modal over the current page. */
export function inAppHref(item: NavItem, pathname: string): string {
  if (item === SETTINGS_ROOT) return settingsHref(pathname, "accounts");
  const settings = SETTINGS_NAV.find((entry) => entry.href === item.href);
  return settings ? settingsHref(pathname, settings.section) : item.href;
}

export function collectionHref(collectionId: number): string {
  return `/collections/${collectionId}`;
}

export const NEW_COLLECTION_HREF = "/collections/new";

/** Exact match or a sub-page of `href`. */
export function isActive(pathname: string, href: string): boolean {
  return pathname === href || pathname.startsWith(`${href}/`);
}
