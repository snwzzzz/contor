import { ChevronRight, X, type LucideIcon } from "lucide-react";
import Link from "next/link";
import { Fragment, type ReactNode } from "react";
import { Button } from "@/components/ui/button";
import { tintVividClass, type Tint } from "@/lib/collection-tint";
import { collectionHref, HOME_HREF, navItem } from "@/lib/navigation";
import { cn } from "@/lib/utils";

export interface Crumb {
  label: string;
  href: string;
}

const HOME_CRUMB: Crumb = { label: "Home", href: HOME_HREF };

/** The collections overview, the first level above every collection, record and report. */
export const COLLECTIONS_CRUMB: Crumb = { label: "Collections", href: "/collections" };

/** The assistant's system collections have their own page; their records lead back through it, not the table. */
const SECTION_PAGES: Record<string, string> = { events: "/calendar", tasks: "/tasks" };

/**
 * The levels above a record or report of a collection: Calendar or To-dos for their system collections, otherwise
 * Collections › the collection. A fresh array, so callers may append the record.
 */
export function collectionTrail(collection: { id: number; slug?: string; name?: string }): Crumb[] {
  const section = collection.slug ? SECTION_PAGES[collection.slug] : undefined;
  if (section) return [{ label: navItem(section).title, href: section }];
  return [COLLECTIONS_CRUMB, { label: collection.name ?? "Collection", href: collectionHref(collection.id) }];
}

interface PageTrailProps {
  /** The levels between Home and this page (Home is always first). */
  trail?: readonly Crumb[];
  /** This page's name, the last (unlinked) crumb. */
  current: string;
  /** Show Close at the end of the trail (pages without a title row); `PageHeader` puts it on its title row instead. */
  closable?: boolean;
  className?: string;
}

/** Breadcrumbs from Home to the current page, and a Close (✕) at the end that goes back one level. */
export function PageTrail({ trail = [], current, closable = true, className }: PageTrailProps) {
  const crumbs = [HOME_CRUMB, ...trail];
  return (
    <div className={cn("flex h-7 items-center gap-2", className)}>
      <nav aria-label="Breadcrumb" className="min-w-0">
        <ol className="flex min-w-0 items-center gap-1 text-xs text-muted-foreground">
          {crumbs.map((crumb) => (
            <Fragment key={crumb.href}>
              <li className="shrink-0">
                <Link href={crumb.href} className="rounded-sm underline-offset-4 outline-none hover:text-foreground hover:underline focus-visible:ring-2 focus-visible:ring-ring">
                  {crumb.label}
                </Link>
              </li>
              <li aria-hidden="true" className="shrink-0">
                <ChevronRight className="size-3" />
              </li>
            </Fragment>
          ))}
          <li aria-current="page" className="truncate text-foreground">
            {current}
          </li>
        </ol>
      </nav>
      {closable ? <CloseLink trail={trail} className="ml-auto" /> : null}
    </div>
  );
}

/** ✕ that closes the page: back to the last level of the trail (Home when there is none). */
function CloseLink({ trail = [], className }: { trail?: readonly Crumb[]; className?: string }) {
  const parent = trail.at(-1) ?? HOME_CRUMB;
  return (
    <Button asChild variant="ghost" size="icon-sm" className={cn("text-muted-foreground", className)}>
      <Link href={parent.href} aria-label="Close">
        <X aria-hidden="true" />
      </Link>
    </Button>
  );
}

interface PageHeaderProps {
  title: string;
  /** A short second part after the title, set apart by a bar ("Organizations | Collection"). */
  subtitle?: string;
  description?: string;
  /** The page's commands (`PageCommands`), shown between the title and Close. */
  actions?: ReactNode;
  /** Section icon, shown bare in the section's color next to the title. */
  icon?: LucideIcon;
  tint?: Tint;
  /** A picture in place of the icon (a record's photo or initial), about 32px. */
  media?: ReactNode;
  /** The levels between Home and this page; Close goes to the last one (Home when empty). */
  trail?: readonly Crumb[];
}

/** Top of every page: breadcrumbs, then icon, title (| subtitle), the page's commands and Close, then the description. */
export function PageHeader({ title, subtitle, description, actions, icon: Icon, tint, media, trail }: PageHeaderProps) {
  return (
    <header className="grid gap-1">
      <PageTrail trail={trail} current={title} closable={false} />
      <div className="flex min-w-0 items-center gap-4">
        <div className="flex min-w-0 shrink items-center gap-2.5">
          {media ?? (Icon ? (
            <Icon
              data-slot="page-icon"
              aria-hidden="true"
              strokeWidth={1.75}
              className={cn("size-6 shrink-0", tint ? tintVividClass(tint) : "text-muted-foreground")}
            />
          ) : null)}
          <PageTitle title={title} subtitle={subtitle} />
        </div>
        {actions ? <div className="min-w-0 flex-1 border-l pl-3">{actions}</div> : <div className="flex-1" />}
        <CloseLink trail={trail} />
      </div>
      {description ? <p className="text-sm text-muted-foreground">{description}</p> : null}
    </header>
  );
}

/** The page's h1 with an optional light "| subtitle" next to it (outside the heading). */
export function PageTitle({ title, subtitle }: { title: string; subtitle?: string }) {
  return (
    <div className="flex min-w-0 items-baseline gap-2 text-2xl tracking-tight">
      <h1 className="truncate font-semibold">{title}</h1>
      {subtitle ? (
        <span className="shrink-0 font-light text-muted-foreground">
          <span aria-hidden="true" className="mr-2">
            |
          </span>
          {subtitle}
        </span>
      ) : null}
    </div>
  );
}
