"use client";

import { keepPreviousData } from "@tanstack/react-query";
import { Mail, SearchX } from "lucide-react";
import { useDeferredValue, useRef, useState } from "react";
import { EmptyState } from "@/components/app/EmptyState";
import { EmailThread } from "@/components/app/mail/EmailThread";
import { EmailList } from "@/components/app/mail/EmailList";
import { EmailPager } from "@/components/app/mail/EmailPager";
import { ReadingPanePlaceholder } from "@/components/app/mail/ReadingPanePlaceholder";
import { EmailSearchBar } from "@/components/app/mail/EmailSearchBar";
import { domainOf, matchesEmail } from "@/components/app/mail/mail-format";
import { useListRecordEmails } from "@/lib/api/generated/endpoints/records/records";
import type { EmailSearchField } from "@/lib/api/generated/model/emailSearchField";
import { useSenderPictures } from "@/lib/hooks/useSenderPictures";

/** Emails per page of the record's Emails tab; page 1 is also what the tab's count comes from (same query, fetched once). */
export const RECORD_EMAIL_PAGE_SIZE = 50;

interface RecordEmailsTabProps {
  collectionId: number;
  recordId: number;
}

/**
 * Emails the record comes up in, laid out like the inbox: a filterable list of email boxes (the filter applies to the
 * loaded page) with the pager at its end, and the reading pane.
 */
export function RecordEmailsTab({ collectionId, recordId }: RecordEmailsTabProps) {
  const [pageIndex, setPageIndex] = useState(0);
  const { data: emails } = useListRecordEmails(collectionId, recordId, { page: pageIndex, size: RECORD_EMAIL_PAGE_SIZE }, {
    // Keep showing the current page while the next one loads, instead of blanking the tab.
    query: { retry: false, placeholderData: keepPreviousData },
  });
  const listScroll = useRef<HTMLDivElement>(null);
  const [selected, setSelected] = useState<number | null>(null);
  const [text, setText] = useState("");
  const [field, setField] = useState<EmailSearchField>("ALL");
  const deferredText = useDeferredValue(text);
  const items = emails?.items ?? [];
  const pictures = useSenderPictures(items.flatMap((item) => domainOf(item.fromAddress) ?? []));

  /** The pager sits at the end of the list: start the next page at its top. */
  function goToPage(index: number) {
    setPageIndex(index);
    if (listScroll.current) listScroll.current.scrollTop = 0;
  }

  /** Contact photo first, then the sender domain's logo. */
  function picture(address: string | undefined): string | undefined {
    return pictures.contactPhoto(address) ?? pictures.logo(domainOf(address));
  }

  if (!emails) {
    return <p className="text-sm text-muted-foreground">Loading emails…</p>;
  }
  if (items.length === 0) {
    return <EmptyState icon={Mail} title="No emails yet" description="Emails from this record's addresses or domain, and the ones it was found in, show up here." />;
  }

  const visible = items.filter((item) => matchesEmail(item, deferredText, field));
  const filtering = deferredText.trim() !== "";

  return (
    // Fills the tab panel on large screens (RecordDetail makes the page a column); the panes scroll on their own.
    <div className="flex min-h-[32rem] flex-col gap-6 lg:min-h-80 lg:flex-1 lg:flex-row">
      {/* Message list: filter on top, the email boxes below in their own scroll area */}
      <section aria-label="Email list" className="flex min-h-0 flex-col gap-3 max-lg:max-h-[55svh] lg:w-[24rem] lg:shrink-0 xl:w-[28rem]">
        <div className="flex shrink-0 items-center gap-2">
          <EmailSearchBar
            text={text}
            field={field}
            placeholder="Filter these emails…"
            className="flex-1"
            onChange={(nextText, nextField) => {
              setText(nextText);
              setField(nextField);
            }}
          />
          <p className="shrink-0 text-xs text-muted-foreground tabular-nums">
            {filtering ? `${visible.length} of ${items.length} emails` : `${items.length} emails`}
          </p>
        </div>
        <div ref={listScroll} data-testid="email-list-scroll" className="min-h-0 flex-1 overflow-x-hidden overflow-y-auto pr-1 [scrollbar-gutter:stable]">
          {visible.length === 0 ? (
            <div className="flex flex-col items-center gap-2 rounded-lg border border-dashed px-4 py-10 text-center text-sm text-muted-foreground">
              <SearchX className="size-5" aria-hidden="true" />
              No email matches.
            </div>
          ) : (
            <EmailList emails={visible} selectedId={selected} onSelect={setSelected} picture={picture} />
          )}
          <EmailPager pageIndex={pageIndex} totalPages={emails.totalPages} totalElements={emails.totalElements} onPage={goToPage} />
        </div>
      </section>

      {/* Reading pane: the subject box and one box per message (like Outlook); the thread scrolls its history itself */}
      <section aria-label="Email details" className="flex min-h-0 min-w-0 flex-1 flex-col max-lg:min-h-[60svh]">
        {selected === null ? <ReadingPanePlaceholder /> : <EmailThread key={selected} emailId={selected} picture={picture} />}
      </section>
    </div>
  );
}
