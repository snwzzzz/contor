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
import { cn } from "@/lib/utils";
import { LoadingList } from "@/components/app/LoadingSkeletons";

function emailCount(count: number): string {
  return `${count} ${count === 1 ? "email" : "emails"}`;
}

/** Emails per page of the record's Emails tab; page 1 is also what the tab's count comes from (same query, fetched once). */
export const RECORD_EMAIL_PAGE_SIZE = 50;

interface RecordEmailsTabProps {
  collectionId: number;
  recordId: number;
}

/**
 * Emails the record comes up in, laid out like the inbox but in the page flow: a filterable list of email boxes (the
 * filter applies to the loaded page) with the pager at its end, and a reading pane that stays in view beside it.
 */
export function RecordEmailsTab({ collectionId, recordId }: RecordEmailsTabProps) {
  const [pageIndex, setPageIndex] = useState(0);
  const { data: emails } = useListRecordEmails(collectionId, recordId, { page: pageIndex, size: RECORD_EMAIL_PAGE_SIZE }, {
    // Keep showing the current page while the next one loads, instead of blanking the tab.
    query: { retry: false, placeholderData: keepPreviousData },
  });
  const listTop = useRef<HTMLElement>(null);
  const [selected, setSelected] = useState<number | null>(null);
  const [text, setText] = useState("");
  const [field, setField] = useState<EmailSearchField>("ALL");
  const deferredText = useDeferredValue(text);
  const items = emails?.items ?? [];
  const pictures = useSenderPictures(items.flatMap((item) => domainOf(item.fromAddress) ?? []));

  /** The pager sits at the end of the list: bring the top of the list back into view for the next page. */
  function goToPage(index: number) {
    setPageIndex(index);
    listTop.current?.scrollIntoView?.({ block: "start" });
  }

  /** Contact photo first, then the sender domain's logo. */
  function picture(address: string | undefined): string | undefined {
    return pictures.contactPhoto(address) ?? pictures.logo(domainOf(address));
  }

  if (!emails) {
    return <LoadingList label="Loading emails" rows={5} avatar twoLines />;
  }
  if (items.length === 0) {
    return <EmptyState icon={Mail} title="No emails yet" description="Emails from this record's addresses or domain, and the ones it was found in, show up here." />;
  }

  const visible = items.filter((item) => matchesEmail(item, deferredText, field));
  const filtering = deferredText.trim() !== "";

  return (
    // Part of the page: the list grows and the page scrolls; on large screens the reading pane stays in view beside it.
    <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,24rem)_minmax(0,1fr)] xl:grid-cols-[minmax(0,28rem)_minmax(0,1fr)]">
      {/* Message list: filter on top, the email boxes and the pager below */}
      <section ref={listTop} aria-label="Email list" className="grid min-w-0 scroll-mt-4 gap-3">
        <div className="flex items-center gap-2">
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
            {filtering ? `${visible.length} of ${emailCount(items.length)}` : emailCount(items.length)}
          </p>
        </div>
        <div data-testid="email-list" className="grid min-w-0 gap-3">
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

      {/* Reading pane: sticky beside the list, below it on small screens. An open conversation takes the height it
          needs, at most the screen (then the thread scrolls its history itself); the placeholder stays compact. */}
      <section
        aria-label="Email details"
        className={cn(
          "flex min-w-0 flex-col lg:sticky lg:top-3",
          selected === null ? "min-h-60" : "lg:max-h-[calc(100dvh-var(--header-height)-3rem)]",
        )}
      >
        {selected === null ? <ReadingPanePlaceholder /> : <EmailThread key={selected} emailId={selected} picture={picture} />}
      </section>
    </div>
  );
}
