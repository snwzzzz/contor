"use client";

import { ChevronLeft, ChevronRight, Clock, Inbox, Mail, Paperclip, Search } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { AppButton } from "@/components/app/AppButton";
import { EmptyState } from "@/components/app/EmptyState";
import { ErrorMessage } from "@/components/app/ErrorMessage";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Separator } from "@/components/ui/separator";
import { useGetEmail, useListEmails, useListMailAccounts } from "@/lib/api/generated/endpoints/mail/mail";
import type { EmailLabelDto } from "@/lib/api/generated/model/emailLabelDto";
import type { EmailSummaryDto } from "@/lib/api/generated/model/emailSummaryDto";

const PAGE_SIZE = 50;

/** Unified inbox over all connected mailboxes, filterable by account. */
export function InboxView() {
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

  const { data: activeEmail, isLoading: isLoadingEmail } = useGetEmail(
    selectedEmailId ?? 0,
    { query: { enabled: selectedEmailId !== null } },
  );

  const emails = page?.items ?? [];
  const totalPages = page?.totalPages ?? 0;
  const addressByAccount = new Map((accounts ?? []).map((account) => [account.id, account.emailAddress]));
  const showAccountOnRows = accountId === null && addressByAccount.size > 1;

  function selectAccount(id: number | null) {
    setAccountId(id);
    setPageIndex(0);
    setSelectedEmailId(null);
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-3">
        <div className="relative flex-1">
          <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
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
      </div>

      {accounts && accounts.length > 0 ? (
        <div className="flex flex-wrap gap-2" role="group" aria-label="Filter by account">
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
            <Link href="/settings/accounts">
              <AppButton variant="outline">Manage Accounts</AppButton>
            </Link>
          }
        />
      ) : (
        <div className="grid grid-cols-1 gap-4 lg:grid-cols-12">
          {/* Email List */}
          <div className="space-y-2 lg:col-span-5">
            {emails.map((email: EmailSummaryDto) => {
              const isSelected = email.id === selectedEmailId;
              return (
                <button
                  type="button"
                  key={email.id}
                  onClick={() => setSelectedEmailId(email.id)}
                  className={`w-full rounded-lg border p-3.5 text-left transition-colors ${
                    isSelected
                      ? "border-primary bg-muted/60"
                      : "border-border hover:bg-muted/40"
                  } ${!email.read ? "font-medium" : ""}`}
                >
                  <div className="flex items-center justify-between gap-2 text-xs text-muted-foreground">
                    <span className="truncate">{email.fromName || email.fromAddress}</span>
                    {showAccountOnRows ? (
                      <Badge variant="outline" className="ml-auto truncate text-[10px] font-normal">
                        {addressByAccount.get(email.accountId)}
                      </Badge>
                    ) : null}
                    <span className="shrink-0">
                      {new Date(email.receivedAt).toLocaleDateString()}
                    </span>
                  </div>

                  <div className="mt-1 flex items-center justify-between gap-2">
                    <h3 className="line-clamp-1 text-sm font-semibold">{email.subject || "(No subject)"}</h3>
                    {email.hasAttachments ? (
                      <Paperclip className="size-3.5 shrink-0 text-muted-foreground" />
                    ) : null}
                  </div>

                  {email.snippet ? (
                    <p className="mt-1 line-clamp-2 text-xs text-muted-foreground font-normal">
                      {email.snippet}
                    </p>
                  ) : null}

                  {email.labels && email.labels.length > 0 ? (
                    <div className="mt-2 flex flex-wrap gap-1">
                      {email.labels.map((lbl: EmailLabelDto) => (
                        <Badge key={lbl.labelId} variant="secondary" className="text-[10px]">
                          {lbl.name}
                        </Badge>
                      ))}
                    </div>
                  ) : null}
                </button>
              );
            })}
            {totalPages > 1 ? (
              <div className="flex items-center justify-between pt-2 text-xs text-muted-foreground">
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
          </div>

          {/* Email Detail Preview */}
          <div className="lg:col-span-7">
            {selectedEmailId === null ? (
              <Card className="flex h-96 items-center justify-center border-dashed">
                <div className="text-center text-sm text-muted-foreground">
                  <Mail className="mx-auto mb-2 size-8 text-muted-foreground/60" />
                  Select an email to view its details
                </div>
              </Card>
            ) : isLoadingEmail ? (
              <Card className="flex h-96 items-center justify-center">
                <div className="text-sm text-muted-foreground">Loading email content...</div>
              </Card>
            ) : activeEmail ? (
              <Card>
                <CardHeader className="space-y-2">
                  <div className="flex items-start justify-between gap-4">
                    <CardTitle className="text-lg">{activeEmail.subject || "(No subject)"}</CardTitle>
                    <Badge variant="outline" className="shrink-0 text-xs gap-1">
                      <Clock className="size-3" />
                      {new Date(activeEmail.receivedAt).toLocaleString()}
                    </Badge>
                  </div>
                  <div className="text-xs text-muted-foreground">
                    <span className="font-semibold text-foreground">From: </span>
                    {activeEmail.fromName ? `${activeEmail.fromName} <${activeEmail.fromAddress}>` : activeEmail.fromAddress}
                  </div>
                  {activeEmail.toRecipients && activeEmail.toRecipients.length > 0 ? (
                    <div className="text-xs text-muted-foreground">
                      <span className="font-semibold text-foreground">To: </span>
                      {activeEmail.toRecipients.map((r) => r.name || r.address).join(", ")}
                    </div>
                  ) : null}
                </CardHeader>
                <Separator />
                <CardContent className="pt-4 text-sm leading-relaxed">
                  {activeEmail.bodyText ? (
                    <div className="whitespace-pre-wrap font-sans">{activeEmail.bodyText}</div>
                  ) : activeEmail.bodyHtml ? (
                    <iframe
                      sandbox="allow-same-origin"
                      srcDoc={activeEmail.bodyHtml}
                      title="Email Content"
                      className="w-full min-h-[350px] rounded border-0"
                    />
                  ) : (
                    <p className="italic text-muted-foreground">No message body provided.</p>
                  )}
                </CardContent>
              </Card>
            ) : null}
          </div>
        </div>
      )}
    </div>
  );
}
