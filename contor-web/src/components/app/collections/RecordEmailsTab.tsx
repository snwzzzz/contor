"use client";

import { Mail } from "lucide-react";
import { useState } from "react";
import { EmptyState } from "@/components/app/EmptyState";
import { EmailHtmlBody } from "@/components/app/mail/EmailHtmlBody";
import { useGetEmail } from "@/lib/api/generated/endpoints/mail/mail";
import type { EmailPageDto } from "@/lib/api/generated/model/emailPageDto";
import { cn } from "@/lib/utils";

const LIST_DATE = new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "short", year: "numeric" });
const FULL_DATE = new Intl.DateTimeFormat("en-GB", { weekday: "short", day: "numeric", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit" });

/** Emails the record comes up in: a list on the left, the selected email on the right. */
export function RecordEmailsTab({ emails }: { emails: EmailPageDto | undefined }) {
  const [selected, setSelected] = useState<number | null>(null);
  const { data: email, isLoading } = useGetEmail(selected ?? 0, { query: { enabled: selected !== null } });

  if (!emails) {
    return <p className="text-sm text-muted-foreground">Loading emails…</p>;
  }
  if (emails.items.length === 0) {
    return <EmptyState icon={Mail} title="No emails yet" description="Emails from this record's addresses or domain, and the ones it was found in, show up here." />;
  }

  return (
    <div className="grid min-h-[28rem] overflow-hidden rounded-xl border lg:grid-cols-[22rem_1fr]">
      <ul className="max-h-[36rem] divide-y overflow-y-auto border-b lg:border-r lg:border-b-0">
        {emails.items.map((item) => (
          <li key={item.id}>
            <button
              type="button"
              onClick={() => setSelected(item.id)}
              aria-current={item.id === selected ? "true" : undefined}
              className={cn("grid w-full gap-0.5 px-4 py-3 text-left hover:bg-muted/50", item.id === selected && "bg-muted")}
            >
              <span className="flex items-baseline justify-between gap-2">
                <span className="truncate text-sm font-medium">{item.subject || "(no subject)"}</span>
                <span className="shrink-0 text-xs text-muted-foreground">{LIST_DATE.format(new Date(item.receivedAt))}</span>
              </span>
              <span className="truncate text-xs text-muted-foreground">{item.fromName || item.fromAddress}</span>
              {item.snippet ? <span className="line-clamp-2 text-xs text-muted-foreground">{item.snippet}</span> : null}
            </button>
          </li>
        ))}
      </ul>
      <div className="min-w-0 overflow-y-auto p-6">
        {selected === null ? (
          <p className="text-sm text-muted-foreground">Select an email to read it.</p>
        ) : isLoading || !email ? (
          <p className="text-sm text-muted-foreground">Loading…</p>
        ) : (
          <article className="grid gap-4">
            <header className="grid gap-1">
              <h3 className="text-lg font-semibold">{email.subject || "(no subject)"}</h3>
              <p className="text-sm text-muted-foreground">
                {email.fromName ? `${email.fromName} <${email.fromAddress}>` : email.fromAddress} · {FULL_DATE.format(new Date(email.receivedAt))}
              </p>
            </header>
            {email.bodyHtml ? <EmailHtmlBody html={email.bodyHtml} /> : <p className="text-sm whitespace-pre-line">{email.bodyText}</p>}
          </article>
        )}
      </div>
    </div>
  );
}
