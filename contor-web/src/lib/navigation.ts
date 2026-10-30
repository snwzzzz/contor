import {
  Bot,
  Braces,
  Brain,
  CalendarDays,
  CircleUser,
  House,
  Inbox,
  KeyRound,
  ListFilter,
  ListTodo,
  MessagesSquare,
  ScanSearch,
  Network,
  Settings,
  ShieldCheck,
  SlidersHorizontal,
  Sparkles,
  Tags,
  type LucideIcon,
} from "lucide-react";
import { CALENDAR_TINT, TASKS_TINT, type Tint } from "@/lib/collection-tint";

export interface NavItem {
  title: string;
  href: string;
  icon: LucideIcon;
  /** Section color (sidebar icon, page title icon). */
  tint?: Tint;
}

export const HOME_HREF = "/";

/** Top-level sections (sidebar + command palette share this list). */
export const PRIMARY_NAV: readonly NavItem[] = [
  { title: "Home", href: HOME_HREF, icon: House },
  { title: "Inbox", href: "/inbox", icon: Inbox, tint: 8 },
  { title: "Calendar", href: "/calendar", icon: CalendarDays, tint: CALENDAR_TINT },
  { title: "To-dos", href: "/tasks", icon: ListTodo, tint: TASKS_TINT },
];

/** What the assistant proposes and found (the "AI insights" group of the sidebar). */
export const AI_INSIGHTS_NAV: readonly NavItem[] = [
  { title: "Proposals", href: "/proposals", icon: Sparkles, tint: 10 },
  { title: "Findings", href: "/findings", icon: ScanSearch, tint: 6 },
];

/** Chat with the assistant about everything in contor (top bar). */
export const CHAT_NAV: NavItem = { title: "Chat", href: "/chat", icon: MessagesSquare, tint: 7 };

/** The apps page: connected external APIs (the top bar's External APIs menu lists them too). */
export const APPS_HREF = "/apps";

export const DATA_NAV: readonly NavItem[] = [
  { title: "External APIs", href: APPS_HREF, icon: Braces, tint: 7 },
  { title: "Relations", href: "/relations", icon: Network, tint: 2 },
  { title: "Queries", href: "/queries", icon: ListFilter, tint: 9 },
  { title: "Rules & Labels", href: "/rules", icon: Tags, tint: 3 },
];

/** Settings open as a modal over the current page, addressed by `?settings=<section>`. */
export const SETTINGS_PARAM = "settings";

export const SETTINGS_SECTIONS = ["account", "general", "accounts", "ai", "autonomy", "security"] as const;
export type SettingsSection = (typeof SETTINGS_SECTIONS)[number];

export interface SettingsNavItem extends NavItem {
  section: SettingsSection;
  description: string;
}

/** `href` (/settings/<section>) redirects into the modal; in-app links use {@link settingsHref}. */
export const SETTINGS_NAV: readonly SettingsNavItem[] = [
  {
    title: "My account",
    href: "/settings/account",
    section: "account",
    icon: CircleUser,
    description: "Who is signed in. Editing your account comes later.",
  },
  { title: "General", href: "/settings/general", section: "general", icon: SlidersHorizontal, description: "Appearance and language." },
  { title: "Accounts", href: "/settings/accounts", section: "accounts", icon: KeyRound, description: "Connected mailboxes." },
  { title: "AI Models", href: "/settings/ai", section: "ai", icon: Brain, description: "Ollama and the local models: what is installed, downloads." },
  {
    title: "Autonomy",
    href: "/settings/autonomy",
    section: "autonomy",
    icon: Bot,
    description: "Set up the assistant, which mailboxes it reads, what it may do without asking, and the inbox analysis.",
  },
  { title: "Security", href: "/settings/security", section: "security", icon: ShieldCheck, description: "Password and two-factor authentication." },
];

/** The AI setup wizard opens as a modal over the current page, addressed by `?ai-setup=1`. */
export const AI_SETUP_PARAM = "ai-setup";

/** The current page with the AI setup wizard opened. */
export function aiSetupHref(pathname: string): string {
  return `${pathname}?${AI_SETUP_PARAM}=1`;
}

/** The add/edit app modal (external APIs) opens over the current page, addressed by `?app=new` or `?app=<id>`. */
export const APP_PARAM = "app";

/** The page of one app. */
export function appPageHref(apiId: number): string {
  return `${APPS_HREF}/${apiId}`;
}

/** The current page with the app modal opened: a new app, or the saved one with `apiId`. */
export function appHref(pathname: string, apiId: number | "new"): string {
  return `${pathname}?${APP_PARAM}=${apiId}`;
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
  const item = [...PRIMARY_NAV, ...AI_INSIGHTS_NAV, CHAT_NAV, ...DATA_NAV].find((entry) => entry.href === href);
  if (!item) throw new Error(`No navigation entry for ${href}`);
  return item;
}

export const ALL_NAV: readonly NavItem[] = [...PRIMARY_NAV, ...AI_INSIGHTS_NAV, CHAT_NAV, ...DATA_NAV, SETTINGS_ROOT, ...SETTINGS_NAV];

/** Where an in-app link to `item` goes: settings entries open the modal over the current page. */
export function inAppHref(item: NavItem, pathname: string): string {
  if (item === SETTINGS_ROOT) return settingsHref(pathname, "general");
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
