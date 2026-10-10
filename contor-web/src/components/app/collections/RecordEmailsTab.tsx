"use client";

import { Mail, SearchX } from "lucide-react";
import { useDeferredValue, useState } from "react";
import { EmptyState } from "@/components/app/EmptyState";
import { EmailReader } from "@/components/app/mail/EmailReader";
import { EmailRow } from "@/components/app/mail/EmailRow";
import { EmailSearchBar } from "@/components/app/mail/EmailSearchBar";
import { domainOf, matchesEmail } from "@/components/app/mail/mail-format";
import { Card } from "@/components/ui/card";
import { useGetEmail } from "@/lib/api/generated/endpoints/mail/mail";
import type { EmailPageDto } from "@/lib/api/generated/model/emailPageDto";
import type { EmailSearchField } from "@/lib/api/generated/model/emailSearchField";
import { useSenderPictures } from "@/lib/hooks/useSenderPictures";

/**
 * Emails the record comes up in, laid out like the inbox: a filterable list of email cards in its own scroll area on
 * the left, the selected email in a details pane on the right.
 */
export function RecordEmailsTab({ emails }: { emails: EmailPageDto | undefined }) {
  const [selected, setSelected] = useState<number | null>(null);
  const [text, setText] = useState("");
  const [field, setField] = useState<EmailSearchField>("ALL");
  const deferredText = useDeferredValue(text);
  const { data: email, isLoading } = useGetEmail(selected ?? 0, { query: { enabled: selected !== null } });
  const items = emails?.items ?? [];
  const pictures = useSenderPictures(items.flatMap((item) => domainOf(item.fromAddress) ?? []));

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
    <div className="flex flex-col gap-4">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <EmailSearchBar
          text={text}
          field={field}
          placeholder="Filter these emails…"
          className="flex-1 sm:max-w-2xl"
          onChange={(nextText, nextField) => {
            setText(nextText);
            setField(nextField);
          }}
        />
        <p className="shrink-0 text-xs text-muted-foreground tabular-nums">
          {filtering ? `${visible.length} of ${items.length} emails` : `${items.length} emails`}
        </p>
      </div>

      <div className="flex min-h-[32rem] flex-col gap-4 lg:h-[calc(100svh-18rem)] lg:flex-row">
        {/* Email list: its own scroll area, fixed width */}
        <section aria-label="Email list" className="flex min-h-0 flex-col max-lg:max-h-[45svh] lg:w-96 lg:shrink-0 xl:w-[28rem]">
          <div className="min-h-0 flex-1 space-y-2 overflow-x-hidden overflow-y-auto pr-1">
            {visible.length === 0 ? (
              <div className="flex flex-col items-center gap-2 rounded-lg border border-dashed px-4 py-10 text-center text-sm text-muted-foreground">
                <SearchX className="size-5" aria-hidden="true" />
                No email matches.
              </div>
            ) : (
              visible.map((item) => (
                <EmailRow key={item.id} email={item} selected={item.id === selected} picture={picture(item.fromAddress)} onSelect={setSelected} />
              ))
            )}
          </div>
        </section>

        {/* Email details: the rest of the width, stays put while the list scrolls */}
        <section aria-label="Email details" className="flex min-h-0 min-w-0 flex-1 flex-col">
          {selected === null ? (
            <Card className="flex h-full items-center justify-center border-dashed">
              <div className="text-center text-sm text-muted-foreground">
                <Mail className="mx-auto mb-2 size-8 text-muted-foreground/60" aria-hidden="true" />
                Select an email to view its details
              </div>
            </Card>
          ) : isLoading || !email ? (
            <Card className="flex h-full items-center justify-center">
              <div className="text-sm text-muted-foreground">Loading email content…</div>
            </Card>
          ) : (
            <Card className="h-full min-h-0 overflow-y-auto p-6">
              <EmailReader email={email} picture={picture(email.fromAddress)} />
            </Card>
          )}
        </section>
      </div>
    </div>
  );
}
