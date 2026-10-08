"use client";

import { ChevronLeft, ChevronRight, Clock, Inbox, Mail, Paperclip, Search } from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useState } from "react";
import { AppButton } from "@/components/app/AppButton";
import { EmptyState } from "@/components/app/EmptyState";
import { ErrorMessage } from "@/components/app/ErrorMessage";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Separator } from "@/components/ui/separator";
import { useGetEmail, useListEmails, useListMailAccounts } from "@/lib/api/generated/endpoints/mail/mail";
import { useListContactPhotos, useListSenderLogos } from "@/lib/api/generated/endpoints/senders/senders";
import type { EmailLabelDto } from "@/lib/api/generated/model/emailLabelDto";
import type { EmailSummaryDto } from "@/lib/api/generated/model/emailSummaryDto";
import type { MailAccountDto } from "@/lib/api/generated/model/mailAccountDto";
import { settingsHref } from "@/lib/navigation";
import { cn } from "@/lib/utils";
import { AccountAvatar } from "./AccountAvatar";
import { EmailHtmlBody } from "./EmailHtmlBody";
import { domainOf } from "./mail-format";
import { PictureAvatar } from "./PictureAvatar";

const PAGE_SIZE = 50;

/**
 * Unified inbox over all connected mailboxes, filterable by account. The list scrolls on its own; the reading pane
 * takes the rest of the width and stays put while scrolling through emails.
 */
export function InboxView() {
  const pathname = usePathname();
  const [search, setSearch] = useState("");
  const [accountId, setAccountId] = useState<number | null>(null);
  const [pageIndex, setPageIndex] = useState(0);
  const [selectedEmailId, setSelectedEmailId] = useState<number | null>(null);

  const { data: accounts } = useListMailAccounts();
  const query = search.trim();
  const { data: page, isLoading, error } = useListEmails({
    ...(query ? { q: query } : {}),
    ...(accountId !== null ? { accountId } : {}),
    page: pageIndex,
    size: PAGE_SIZE,
  });

  const { data: activeEmail, isLoading: isLoadingEmail } = useGetEmail(selectedEmailId ?? 0, {
    query: { enabled: selectedEmailId !== null },
  });

  const emails = page?.items ?? [];
  const senderDomains = [...new Set(emails.map((email) => domainOf(email.fromAddress)).filter((domain) => domain !== undefined))];
  const { data: logoDomains } = useListSenderLogos(
    { domain: senderDomains },
    { query: { enabled: senderDomains.length > 0, staleTime: Infinity } },
  );
  const { data: contactPhotos } = useListContactPhotos({ query: { staleTime: 5 * 60_000 } });
  const contactPhotoByAddress = new Map((contactPhotos ?? []).map((photo) => [photo.emailAddress, photo.id]));
  const domainsWithLogo = new Set(logoDomains ?? []);

  /** Contact photo first, then the sender domain's logo. */
  function senderPicture(address: string | undefined): string | undefined {
    const contactPhotoId = address ? contactPhotoByAddress.get(address.toLowerCase()) : undefined;
    if (contactPhotoId !== undefined) return `/api/senders/contact-photos/${contactPhotoId}`;
    const domain = domainOf(address);
    return domain && domainsWithLogo.has(domain) ? `/api/senders/logos/${domain}` : undefined;
  }
  const totalPages = page?.totalPages ?? 0;
  const accountById = new Map((accounts ?? []).map((account) => [account.id, account]));
  const showAccountOnRows = accountId === null && accountById.size > 1;

  function selectAccount(id: number | null) {
    setAccountId(id);
    setPageIndex(0);
    setSelectedEmailId(null);
  }

  return (
    <div className="flex min-h-0 flex-1 flex-col gap-4">
      <div className="relative shrink-0">
        <Search className="absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" />
        <Input
          value={search}
          onChange={(e) => {
            setSearch(e.target.value);
            setPageIndex(0);
          }}
          placeholder="Search emails by subject, sender..."
          className="pl-9"
        />
      </div>

      {accounts && accounts.length > 0 ? (
        <div className="flex shrink-0 flex-wrap gap-2" role="group" aria-label="Filter by account">
          <AppButton
            size="sm"
            variant={accountId === null ? "default" : "outline"}
            aria-pressed={accountId === null}
            onClick={() => selectAccount(null)}
          >
            All accounts
          </AppButton>
          {accounts.map((account) => (
            <AppButton
              key={account.id}
              size="sm"
              variant={accountId === account.id ? "default" : "outline"}
              aria-pressed={accountId === account.id}
              onClick={() => selectAccount(account.id)}
            >
              <AccountAvatar account={account} size={18} />
              {account.emailAddress}
            </AppButton>
          ))}
        </div>
      ) : null}

      {isLoading ? (
        <div className="py-8 text-center text-sm text-muted-foreground">Loading emails...</div>
      ) : error ? (
        <ErrorMessage message={error.message} />
      ) : emails.length === 0 ? (
        <EmptyState
          icon={Inbox}
          title="No emails found"
          description={
            query
              ? "No emails matched your search query."
              : accountId !== null
                ? "No emails synced for this account yet."
                : "No emails synced yet. Connect an account or trigger a sync."
          }
          action={
            <Link href={settingsHref(pathname, "accounts")} scroll={false}>
              <AppButton variant="outline">Manage Accounts</AppButton>
            </Link>
          }
        />
      ) : (
        <div className="flex min-h-0 flex-1 flex-col gap-4 lg:flex-row">
          {/* Email list: its own scroll area, fixed width */}
          <section
            aria-label="Email list"
            className="flex min-h-0 flex-col max-lg:max-h-[45svh] lg:w-96 lg:shrink-0 xl:w-[28rem]"
          >
            <div className="min-h-0 flex-1 space-y-2 overflow-x-hidden overflow-y-auto pr-1">
              {emails.map((email) => (
                <EmailRow
                  key={email.id}
                  email={email}
                  selected={email.id === selectedEmailId}
                  account={showAccountOnRows ? accountById.get(email.accountId) : undefined}
                  picture={senderPicture(email.fromAddress)}
                  onSelect={setSelectedEmailId}
                />
              ))}
            </div>
            {totalPages > 1 ? (
              <div className="flex shrink-0 items-center justify-between pt-3 text-xs text-muted-foreground">
                <span>
                  Page {pageIndex + 1} of {totalPages} · {page?.totalElements} emails
                </span>
                <div className="flex gap-1">
                  <AppButton
                    size="sm"
                    variant="outline"
                    disabled={pageIndex === 0}
                    onClick={() => setPageIndex((current) => current - 1)}
                  >
                    <ChevronLeft className="size-3.5" />
                    Previous
                  </AppButton>
                  <AppButton
                    size="sm"
                    variant="outline"
                    disabled={pageIndex + 1 >= totalPages}
                    onClick={() => setPageIndex((current) => current + 1)}
                  >
                    Next
                    <ChevronRight className="size-3.5" />
                  </AppButton>
                </div>
              </div>
            ) : null}
          </section>

          {/* Email details: the rest of the width, stays put while the list scrolls */}
          <section aria-label="Email details" className="flex min-h-0 min-w-0 flex-1 flex-col">
            {selectedEmailId === null ? (
              <Card className="flex h-full items-center justify-center border-dashed">
                <div className="text-center text-sm text-muted-foreground">
                  <Mail className="mx-auto mb-2 size-8 text-muted-foreground/60" />
                  Select an email to view its details
                </div>
              </Card>
            ) : isLoadingEmail ? (
              <Card className="flex h-full items-center justify-center">
                <div className="text-sm text-muted-foreground">Loading email content...</div>
              </Card>
            ) : activeEmail ? (
              <Card className="flex h-full min-h-0 flex-col gap-0 py-0">
                <CardHeader className="shrink-0 space-y-2 py-4">
                  <div className="flex items-start justify-between gap-4">
                    <CardTitle className="text-lg">{activeEmail.subject || "(No subject)"}</CardTitle>
                    <Badge variant="outline" className="shrink-0 gap-1 text-xs">
                      <Clock className="size-3" />
                      {new Date(activeEmail.receivedAt).toLocaleString()}
                    </Badge>
                  </div>
                  <div className="flex items-center gap-2 text-xs text-muted-foreground">
                    <PictureAvatar
                      label={activeEmail.fromName || activeEmail.fromAddress || "?"}
                      src={senderPicture(activeEmail.fromAddress)}
                      size={28}
                    />
                    <span>
                      <span className="font-semibold text-foreground">From: </span>
                      {activeEmail.fromName
                        ? `${activeEmail.fromName} <${activeEmail.fromAddress}>`
                        : activeEmail.fromAddress}
                    </span>
                  </div>
                  {activeEmail.toRecipients.length > 0 ? (
                    <div className="text-xs text-muted-foreground">
                      <span className="font-semibold text-foreground">To: </span>
                      {activeEmail.toRecipients.map((r) => r.name || r.address).join(", ")}
                    </div>
                  ) : null}
                </CardHeader>
                <Separator />
                <CardContent className="min-h-0 flex-1 overflow-y-auto py-4 text-sm leading-relaxed">
                  {activeEmail.bodyText ? (
                    <div className="font-sans whitespace-pre-wrap">{activeEmail.bodyText}</div>
                  ) : activeEmail.bodyHtml ? (
                    <EmailHtmlBody html={activeEmail.bodyHtml} />
                  ) : (
                    <p className="text-muted-foreground italic">No message body provided.</p>
                  )}
                </CardContent>
              </Card>
            ) : null}
          </section>
        </div>
      )}
    </div>
  );
}

interface EmailRowProps {
  email: EmailSummaryDto;
  selected: boolean;
  /** Mailbox of the email, shown in the unified view of several accounts. */
  account?: MailAccountDto;
  /** Sender picture URL (contact photo or domain logo). */
  picture?: string;
  onSelect: (emailId: number) => void;
}

function EmailRow({ email, selected, account, picture, onSelect }: EmailRowProps) {
  const sender = email.fromName || email.fromAddress || "";
  return (
    <button
      type="button"
      onClick={() => onSelect(email.id)}
      className={cn(
        "flex w-full gap-3 rounded-lg border p-3.5 text-left transition-colors",
        selected ? "border-primary bg-muted/60" : "border-border hover:bg-muted/40",
        !email.read && "font-medium",
      )}
    >
      <PictureAvatar label={sender || "?"} src={picture} size={32} className="mt-0.5 self-start" />
      <div className="min-w-0 flex-1">
        <div className="flex items-center justify-between gap-2 text-xs text-muted-foreground">
          <span className="min-w-0 truncate">{sender}</span>
          {account ? (
            <Badge variant="outline" className="ml-auto max-w-44 gap-1 truncate text-[10px] font-normal">
              <AccountAvatar account={account} size={14} />
              {account.emailAddress}
            </Badge>
          ) : null}
          <span className="shrink-0">{new Date(email.receivedAt).toLocaleDateString()}</span>
        </div>

        <div className="mt-1 flex items-center justify-between gap-2">
          <h3 className="line-clamp-1 text-sm font-semibold">{email.subject || "(No subject)"}</h3>
          {email.hasAttachments ? <Paperclip className="size-3.5 shrink-0 text-muted-foreground" /> : null}
        </div>

        {email.snippet ? (
          <p className="mt-1 line-clamp-2 text-xs font-normal text-muted-foreground">{email.snippet}</p>
        ) : null}

        {email.labels.length > 0 ? (
          <div className="mt-2 flex flex-wrap gap-1">
            {email.labels.map((lbl: EmailLabelDto) => (
              <Badge key={lbl.labelId} variant="secondary" className="text-[10px]">
                {lbl.name}
              </Badge>
            ))}
          </div>
        ) : null}
      </div>
    </button>
  );
}
