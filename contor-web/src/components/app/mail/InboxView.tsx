"use client";

import { keepPreviousData, useQueryClient } from "@tanstack/react-query";
import { Inbox } from "lucide-react";
import Link from "next/link";
import { usePathname, useSearchParams } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { AppButton } from "@/components/app/AppButton";
import { EmptyState } from "@/components/app/EmptyState";
import { ErrorMessage } from "@/components/app/ErrorMessage";
import { getGetEmailThreadQueryOptions, useListEmails, useListMailAccounts } from "@/lib/api/generated/endpoints/mail/mail";
import { useSenderPictures } from "@/lib/hooks/useSenderPictures";
import type { EmailFolder } from "@/lib/api/generated/model/emailFolder";
import type { EmailSearchField } from "@/lib/api/generated/model/emailSearchField";
import { settingsHref } from "@/lib/navigation";
import { EmailThread } from "./EmailThread";
import { EmailList } from "./EmailList";
import { EmailPager } from "./EmailPager";
import { ReadingPanePlaceholder } from "./ReadingPanePlaceholder";
import { EmailSearchBar } from "./EmailSearchBar";
import { InboxFilter } from "./InboxFilter";
import { FolderSwitch } from "./FolderSwitch";
import { EmailListSkeleton } from "./InboxSkeleton";
import { counterpartAddress, domainOf } from "./mail-format";

const PAGE_SIZE = 50;
/** How long the pointer (or focus) has to rest on a row before its conversation is prefetched. */
const PREVIEW_DELAY_MS = 120;

/**
 * Unified inbox over all connected mailboxes, structured like Outlook: the message list (compact search and filter on
 * top, email boxes under period headings, its own scroll) and the reading pane over the rest of the width: the subject
 * box and each message of the conversation in its own box below it.
 */
export function InboxView() {
  const pathname = usePathname();
  // `/inbox?email=<id>` (links from proposals) opens that email.
  const linked = Number(useSearchParams().get("email"));
  const [search, setSearch] = useState("");
  const [field, setField] = useState<EmailSearchField>("ALL");
  const [accountId, setAccountId] = useState<number | null>(null);
  const [unanswered, setUnanswered] = useState(false);
  const [folder, setFolder] = useState<EmailFolder>("INBOX");
  const [pageIndex, setPageIndex] = useState(0);
  const [selectedEmailId, setSelectedEmailId] = useState<number | null>(Number.isInteger(linked) && linked > 0 ? linked : null);
  const listScroll = useRef<HTMLDivElement>(null);
  const previewTimer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  const queryClient = useQueryClient();
  useEffect(() => () => clearTimeout(previewTimer.current), []);

  const { data: accounts } = useListMailAccounts();
  const query = search.trim();
  const { data: page, isLoading, error } = useListEmails({
    ...(query ? { q: query, field } : {}),
    ...(accountId !== null ? { accountId } : {}),
    ...(unanswered ? { answered: false } : {}),
    ...(folder === "SENT" ? { folder } : {}),
    page: pageIndex,
    size: PAGE_SIZE,
  }, {
    // Paging and filtering keep the current list on screen until the next one arrived.
    query: { placeholderData: keepPreviousData },
  });

  const emails = page?.items ?? [];
  const pictures = useSenderPictures(emails.map((email) => domainOf(counterpartAddress(email))).filter((domain) => domain !== undefined));

  /** Contact photo first, then the sender domain's logo. */
  function senderPicture(address: string | undefined): string | undefined {
    return pictures.contactPhoto(address) ?? pictures.logo(domainOf(address));
  }
  const totalPages = page?.totalPages ?? 0;
  const accountById = new Map((accounts ?? []).map((account) => [account.id, account]));
  const showAccountOnRows = accountId === null && accountById.size > 1;

  function prefetchThread(emailId: number) {
    void queryClient.prefetchQuery(getGetEmailThreadQueryOptions(emailId));
  }

  /** Hover intent: rows the pointer only passes over are not fetched. */
  function startPreview(emailId: number) {
    clearTimeout(previewTimer.current);
    previewTimer.current = setTimeout(() => prefetchThread(emailId), PREVIEW_DELAY_MS);
  }

  function endPreview() {
    clearTimeout(previewTimer.current);
  }

  /** Opens an email and prefetches its neighbours, which are usually read next. */
  function openEmail(emailId: number) {
    setSelectedEmailId(emailId);
    const index = emails.findIndex((email) => email.id === emailId);
    for (const neighbour of [emails[index - 1], emails[index + 1]]) {
      if (index >= 0 && neighbour) prefetchThread(neighbour.id);
    }
  }

  /** The pager sits at the end of the list: start the next page at its top. */
  function goToPage(index: number) {
    setPageIndex(index);
    if (listScroll.current) listScroll.current.scrollTop = 0;
  }

  function selectFolder(next: EmailFolder) {
    setFolder(next);
    setUnanswered(false);
    setPageIndex(0);
    setSelectedEmailId(null);
  }

  function selectAccount(id: number | null) {
    setAccountId(id);
    setPageIndex(0);
    setSelectedEmailId(null);
  }

  return (
    <div className="flex min-h-0 flex-1 flex-col gap-6 lg:flex-row">
      {/* Message list: search and filter on top, the email boxes below in their own scroll area */}
      <section aria-label="Email list" className="flex min-h-0 flex-col gap-3 max-lg:max-h-[55svh] lg:w-[24rem] lg:shrink-0 xl:w-[28rem]">
        <FolderSwitch folder={folder} onChange={selectFolder} />
        <div className="flex shrink-0 items-center gap-2">
          <EmailSearchBar
            suggestions
            folder={folder}
            text={search}
            field={field}
            placeholder="Search mail…"
            className="flex-1"
            onChange={(text, nextField) => {
              setSearch(text);
              setField(nextField);
              setPageIndex(0);
            }}
          />
          <InboxFilter
            accounts={accounts ?? []}
            accountId={accountId}
            onAccountChange={selectAccount}
            unanswered={unanswered}
            hideUnanswered={folder === "SENT"}
            onUnansweredChange={(next) => {
              setUnanswered(next);
              setPageIndex(0);
            }}
          />
        </div>

        <div ref={listScroll} data-testid="email-list-scroll" className="min-h-0 flex-1 overflow-x-hidden overflow-y-auto pr-1 [scrollbar-gutter:stable]">
          {isLoading ? (
            <EmailListSkeleton />
          ) : error ? (
            <ErrorMessage message={error.message} />
          ) : emails.length === 0 ? (
            <EmptyState
              icon={Inbox}
              title={folder === "SENT" && !query && accountId === null ? "No sent mail yet" : "No emails found"}
              description={
                query
                  ? "No emails matched your search query."
                  : folder === "SENT"
                    ? "Your sent mail shows up here after the next sync."
                    : accountId !== null || unanswered
                      ? "No emails match these filters."
                      : "No emails synced yet. Connect an account or trigger a sync."
              }
              action={
                <Link href={settingsHref(pathname, "accounts")} scroll={false}>
                  <AppButton variant="outline">Manage Accounts</AppButton>
                </Link>
              }
            />
          ) : (
            <>
              <EmailList
                emails={emails}
                selectedId={selectedEmailId}
                onSelect={openEmail}
                onPreviewStart={startPreview}
                onPreviewEnd={endPreview}
                picture={senderPicture}
                accountOf={showAccountOnRows ? (email) => accountById.get(email.accountId) : undefined}
              />
              {/* The pager ends the list: it shows up once you scrolled to the last email of the page. */}
              <EmailPager pageIndex={pageIndex} totalPages={totalPages} totalElements={page?.totalElements ?? 0} onPage={goToPage} />
            </>
          )}
        </div>
      </section>

      {/* Reading pane: the subject box and one box per message (like Outlook); the thread scrolls its history itself */}
      <section aria-label="Email details" className="flex min-h-0 min-w-0 flex-1 flex-col max-lg:min-h-[60svh]">
        {selectedEmailId === null ? (
          <ReadingPanePlaceholder />
        ) : (
          <EmailThread
            key={selectedEmailId}
            emailId={selectedEmailId}
            preview={emails.find((email) => email.id === selectedEmailId)}
            picture={senderPicture}
          />
        )}
      </section>
    </div>
  );
}
