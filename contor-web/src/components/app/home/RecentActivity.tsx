"use client";

import { Inbox, MailCheck, Reply, type LucideIcon } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { ErrorMessage } from "@/components/app/ErrorMessage";
import { PictureAvatar } from "@/components/app/mail/PictureAvatar";
import { domainOf } from "@/components/app/mail/mail-format";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { useListEmails } from "@/lib/api/generated/endpoints/mail/mail";
import type { EmailSummaryDto } from "@/lib/api/generated/model/emailSummaryDto";
import type { ListEmailsParams } from "@/lib/api/generated/model/listEmailsParams";
import { ROW_HOVER } from "@/lib/card-hover";
import { isFreeMailDomain, useSenderPictures } from "@/lib/hooks/useSenderPictures";
import { relativeTime } from "@/lib/relative-time";
import { cn } from "@/lib/utils";
import { HomeSection, QuietEmpty, SectionLink } from "./HomeSection";
import { Pager, usePages } from "./Pager";

/** Rows per page; one request loads four pages. */
const ROWS = 6;
const LOADED = ROWS * 4;

interface ActivityTab {
  value: string;
  label: string;
  params: ListEmailsParams;
  empty: { icon: LucideIcon; title: string; description: string };
}

const TABS: readonly ActivityTab[] = [
  {
    value: "recent",
    label: "Recent",
    params: { size: LOADED },
    empty: {
      icon: Inbox,
      title: "No emails yet",
      description: "Connect a mailbox and new mail shows up here.",
    },
  },
  {
    value: "unread",
    label: "Unread",
    params: { read: false, size: LOADED },
    empty: {
      icon: MailCheck,
      title: "Nothing unread",
      description: "You have read every email in your inbox.",
    },
  },
  {
    value: "reply",
    label: "Needs reply",
    params: { answered: false, size: LOADED },
    empty: {
      icon: Reply,
      title: "No emails waiting for a reply",
      description: "Everything that needed an answer got one.",
    },
  },
];

/** The latest emails with tabs (Recent | Unread | Needs reply) as a plain list; each row opens the email in the inbox. */
export function RecentActivity() {
  const [tab, setTab] = useState(TABS[0].value);
  return (
    <HomeSection
      title="Recent activity"
      action={
        <SectionLink href="/inbox" arrow>
          Open mailbox
        </SectionLink>
      }
    >
      <Tabs value={tab} onValueChange={setTab} className="gap-1">
        <TabsList variant="line" className="-ml-1.5">
          {TABS.map((entry) => (
            <TabsTrigger key={entry.value} value={entry.value} className="px-1.5 text-xs">
              {entry.label}
            </TabsTrigger>
          ))}
        </TabsList>
        {TABS.map((entry) => (
          <TabsContent key={entry.value} value={entry.value}>
            <EmailRows tab={entry} />
          </TabsContent>
        ))}
      </Tabs>
    </HomeSection>
  );
}

/** Mounted only for the active tab, so each list is fetched when it is opened. */
function EmailRows({ tab }: { tab: ActivityTab }) {
  const { data, isPending, error } = useListEmails(tab.params);
  const pages = usePages(data?.items ?? [], ROWS);
  const emails = pages.items;
  const domains = emails
    .map((email) => domainOf(email.fromAddress))
    .filter((domain): domain is string => domain !== undefined && !isFreeMailDomain(domain));
  const pictures = useSenderPictures(domains);
  // Fixed per mount: rows are only rendered on the client, after the emails arrived.
  const [now] = useState(() => new Date());

  if (error) return <ErrorMessage message={error.message} />;
  if (isPending) return <RowSkeletons />;
  if (emails.length === 0) {
    const { icon, title, description } = tab.empty;
    return (
      <QuietEmpty
        icon={icon}
        title={title}
        description={description}
        action={
          <Button asChild variant="outline" size="xs" className="mt-1">
            <Link href="/inbox">Go to inbox</Link>
          </Button>
        }
      />
    );
  }

  return (
    <>
      <ul className="-mx-2">
        {emails.map((email) => {
          const domain = domainOf(email.fromAddress);
          const picture = pictures.contactPhoto(email.fromAddress) ?? (isFreeMailDomain(domain) ? undefined : pictures.logo(domain));
          return (
            <li key={email.id}>
              <EmailActivityRow email={email} picture={picture} now={now} />
            </li>
          );
        })}
      </ul>
      <Pager label="Emails" {...pages} />
    </>
  );
}

function EmailActivityRow({ email, picture, now }: { email: EmailSummaryDto; picture: string | undefined; now: Date }) {
  const sender = email.fromName || email.fromAddress || "Unknown sender";
  const unread = !email.read;
  const label = email.labels[0];
  return (
    <Link
      href={`/inbox?email=${email.id}`}
      data-unread={unread ? "true" : undefined}
      className={cn("flex items-center gap-3 rounded-md px-2 py-2 outline-none focus-visible:ring-2 focus-visible:ring-ring", ROW_HOVER)}
    >
      <PictureAvatar label={sender} src={picture} size={24} />
      <span className="grid min-w-0 flex-1 gap-0.5 sm:grid-cols-[minmax(0,10rem)_minmax(0,1fr)] sm:items-center sm:gap-4">
        <span className={cn("truncate text-sm", unread ? "font-medium" : "text-muted-foreground")}>{sender}</span>
        <span className="min-w-0 truncate text-sm">
          <span className={unread ? "font-medium" : undefined}>{email.subject || "(No subject)"}</span>
          {email.snippet ? (
            <>
              <span aria-hidden="true" className="text-muted-foreground">
                {" – "}
              </span>
              <span className="text-muted-foreground">{email.snippet}</span>
            </>
          ) : null}
        </span>
      </span>
      {label ? <span className="hidden shrink-0 rounded-sm border px-1.5 text-xs text-muted-foreground md:inline">{label.name}</span> : null}
      <time dateTime={email.receivedAt} className="w-20 shrink-0 text-right text-xs text-muted-foreground tabular-nums">
        {relativeTime(email.receivedAt, now)}
      </time>
      {/* The only color in the list: a small dot for unread mail. */}
      <span aria-hidden="true" className={cn("size-1.5 shrink-0 rounded-full", unread ? "bg-info" : "bg-transparent")} />
    </Link>
  );
}

const SKELETON_WIDTHS = ["w-3/4", "w-2/3", "w-4/5", "w-1/2", "w-3/5", "w-2/3"] as const;

function RowSkeletons() {
  return (
    <ul aria-label="Loading emails" className="grid">
      {SKELETON_WIDTHS.map((width, index) => (
        <li key={index} className="flex items-center gap-3 py-2">
          <Skeleton className="size-6 rounded-full" />
          <Skeleton className={cn("h-4", width)} />
          <Skeleton className="ml-auto h-3 w-12" />
        </li>
      ))}
    </ul>
  );
}
