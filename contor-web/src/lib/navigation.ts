import {
  Bot,
  Brain,
  CalendarDays,
  Inbox,
  KeyRound,
  ListFilter,
  ListTodo,
  ScanSearch,
  Network,
  Settings,
  ShieldCheck,
  Sparkles,
  Tags,
  type LucideIcon,
} from "lucide-react";
import type { Tint } from "@/lib/collection-tint";

export interface NavItem {
  title: string;
  href: string;
  icon: LucideIcon;
  /** Section color (sidebar icon, page title icon). */
  tint?: Tint;
}

/** Top-level sections (sidebar + command palette share this list). */
export const PRIMARY_NAV: readonly NavItem[] = [
  { title: "Inbox", href: "/inbox", icon: Inbox, tint: 5 },
  { title: "Proposals", href: "/proposals", icon: Sparkles, tint: 6 },
  { title: "Findings", href: "/findings", icon: ScanSearch, tint: 4 },
  { title: "Calendar", href: "/calendar", icon: CalendarDays, tint: 1 },
  { title: "To-dos", href: "/tasks", icon: ListTodo, tint: 3 },
];

export const DATA_NAV: readonly NavItem[] = [
  { title: "Relations", href: "/relations", icon: Network, tint: 2 },
  { title: "Queries", href: "/queries", icon: ListFilter, tint: 5 },
  { title: "Rules & Labels", href: "/rules", icon: Tags, tint: 4 },
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
  { title: "AI Models", href: "/settings/ai", section: "ai", icon: Brain, description: "The local Ollama model and the inbox analysis." },
  { title: "Autonomy", href: "/settings/autonomy", section: "autonomy", icon: Bot, description: "What the AI may read and how much it may do without asking." },
  { title: "Security", href: "/settings/security", section: "security", icon: ShieldCheck, description: "Password and two-factor authentication." },
];

/** The AI setup wizard opens as a modal over the current page, addressed by `?ai-setup=1`. */
export const AI_SETUP_PARAM = "ai-setup";

/** The current page with the AI setup wizard opened. */
export function aiSetupHref(pathname: string): string {
  return `${pathname}?${AI_SETUP_PARAM}=1`;
}

export const SETTINGS_ROOT: NavItem = { title: "Settings", href: "/settings", icon: Settings };

export function isSettingsSection(value: string | null | undefined): value is SettingsSection {
  return SETTINGS_SECTIONS.some((section) => section === value);
}

/** The current page with the settings modal opened at `section`. */
export function settingsHref(pathname: string, section: SettingsSection): string {
  return `${pathname}?${SETTINGS_PARAM}=${section}`;
}

/** The navigation entry of a top-level page (its icon and tint for the page title). */
export function navItem(href: string): NavItem {
  const item = [...PRIMARY_NAV, ...DATA_NAV].find((entry) => entry.href === href);
  if (!item) throw new Error(`No navigation entry for ${href}`);
  return item;
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
