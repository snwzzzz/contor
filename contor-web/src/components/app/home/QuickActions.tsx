import { CalendarDays, Inbox, ListTodo, MessagesSquare, Sparkles, type LucideIcon } from "lucide-react";
import Link from "next/link";
import { CALENDAR_TINT, TASKS_TINT, tintVividClass, type Tint } from "@/lib/collection-tint";
import { cn } from "@/lib/utils";

interface QuickAction {
  label: string;
  href: string;
  icon: LucideIcon;
  tint: Tint;
}

const ACTIONS: readonly QuickAction[] = [
  { label: "View mailbox", href: "/inbox", icon: Inbox, tint: 8 },
  { label: "Open chat", href: "/chat", icon: MessagesSquare, tint: 7 },
  { label: "Calendar", href: "/calendar", icon: CalendarDays, tint: CALENDAR_TINT },
  { label: "To-dos", href: "/tasks", icon: ListTodo, tint: TASKS_TINT },
  { label: "Review proposals", href: "/proposals", icon: Sparkles, tint: 10 },
];

/** Shortcuts to the most used pages: a row of icons with their names, no boxes. */
export function QuickActions() {
  return (
    <section aria-labelledby="home-quick-actions" className="grid gap-2">
      <h2 id="home-quick-actions" className="text-lg font-semibold tracking-tight">
        Quick actions
      </h2>
      <ul className="-mx-2 flex flex-wrap">
        {ACTIONS.map((action) => (
          <li key={action.href}>
            <Link
              href={action.href}
              className="flex w-24 flex-col items-center gap-2 rounded-md px-2 py-3 text-center text-xs outline-none transition-colors hover:bg-accent focus-visible:ring-2 focus-visible:ring-ring"
            >
              <action.icon aria-hidden="true" className={cn("size-7", tintVividClass(action.tint))} strokeWidth={1.75} />
              <span>{action.label}</span>
            </Link>
          </li>
        ))}
      </ul>
    </section>
  );
}
