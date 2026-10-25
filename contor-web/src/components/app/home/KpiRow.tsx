"use client";

import { CalendarDays, Inbox, ListTodo, Sparkles, type LucideIcon } from "lucide-react";
import Link from "next/link";
import { Skeleton } from "@/components/ui/skeleton";
import { useListEmails, useListMailAccounts } from "@/lib/api/generated/endpoints/mail/mail";
import { useCountProposals } from "@/lib/api/generated/endpoints/proposals/proposals";
import { ROW_HOVER, SURFACE_CARD } from "@/lib/card-hover";
import { cn } from "@/lib/utils";
import { eventStart, useTodayAgenda } from "./useTodayAgenda";

/** Only the count matters: one row is the smallest page. */
const UNREAD = { read: false, size: 1 } as const;
const PENDING = { status: "PENDING", group: "ACTIONS" } as const;
const PENDING_HIGH = { ...PENDING, minImportance: "HIGH" } as const;
const TIME = new Intl.DateTimeFormat("en-GB", { hour: "2-digit", minute: "2-digit" });

/** Four numbers at a glance in one strip: unread mail, pending proposals, open to-dos and today's events. */
export function KpiRow() {
  return (
    <section
      aria-label="At a glance"
      // Hairlines between the cells: the strip shows its border color through a 1px gap.
      className={cn(SURFACE_CARD, "grid gap-px overflow-hidden bg-border sm:grid-cols-2 xl:grid-cols-4")}
    >
      <UnreadMail />
      <PendingProposals />
      <AgendaStats />
    </section>
  );
}

function UnreadMail() {
  const { data: unread } = useListEmails(UNREAD);
  const { data: accounts } = useListMailAccounts();
  return (
    <StatCell
      href="/inbox"
      label="Unread mail"
      icon={Inbox}
      value={unread?.totalElements}
      detail={
        !accounts ? undefined : accounts.length === 0 ? "No mailbox connected" : `across ${accounts.length} ${accounts.length === 1 ? "mailbox" : "mailboxes"}`
      }
    />
  );
}

function PendingProposals() {
  const { data: pending } = useCountProposals(PENDING);
  const { data: high } = useCountProposals(PENDING_HIGH);
  return (
    <StatCell
      href="/proposals"
      label="Pending proposals"
      icon={Sparkles}
      value={pending?.count}
      detail={high ? `${high.count} high priority` : undefined}
    />
  );
}

function AgendaStats() {
  const { now, events, eventCount, openTasks, dueTasks, eventsLoading, tasksLoading } = useTodayAgenda();
  const overdue = dueTasks.filter(({ due }) => due.tone === "overdue").length;
  const dueToday = dueTasks.length - overdue;
  const next = events?.find((event) => (eventStart(event)?.getTime() ?? 0) >= now.getTime());
  const nextStart = next ? eventStart(next) : null;

  return (
    <>
      <StatCell
        href="/tasks"
        label="Open to-dos"
        icon={ListTodo}
        value={tasksLoading ? undefined : (openTasks?.length ?? 0)}
        detail={tasksLoading ? undefined : overdue > 0 ? `${overdue} overdue` : dueToday > 0 ? `${dueToday} due today` : "Nothing overdue"}
        alert={overdue > 0}
      />
      <StatCell
        href="/calendar"
        label="Today's events"
        icon={CalendarDays}
        value={eventsLoading ? undefined : (eventCount ?? 0)}
        detail={eventsLoading ? undefined : next && nextStart ? `Next: ${TIME.format(nextStart)} ${next.title ?? ""}`.trim() : "Nothing else today"}
      />
    </>
  );
}

interface StatCellProps {
  href: string;
  label: string;
  icon: LucideIcon;
  /** Undefined while loading. */
  value: number | undefined;
  detail: string | undefined;
  /** The detail needs attention (e.g. overdue to-dos): the one place it gets a color. */
  alert?: boolean;
}

/** One cell of the strip, a whole-cell link: label with a small icon, the number, a line of context. */
function StatCell({ href, label, icon: Icon, value, detail, alert }: StatCellProps) {
  return (
    <Link href={href} className={cn("grid gap-1 bg-surface-raised p-4 outline-none focus-visible:bg-muted", ROW_HOVER)}>
      <span className="flex items-center justify-between text-sm text-muted-foreground">
        {label}
        <Icon aria-hidden="true" className="size-4" strokeWidth={1.75} />
      </span>
      {value === undefined ? (
        <Skeleton className="my-1 h-7 w-12" />
      ) : (
        <span className="text-2xl font-semibold tracking-tight tabular-nums">{value}</span>
      )}
      {detail === undefined ? (
        <Skeleton className="h-4 w-28" />
      ) : (
        <span className={cn("truncate text-xs", alert ? "text-destructive" : "text-muted-foreground")}>{detail}</span>
      )}
    </Link>
  );
}
