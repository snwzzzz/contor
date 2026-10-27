"use client";

import { FileText, Mail } from "lucide-react";
import Link from "next/link";
import { usePathname, useSearchParams } from "next/navigation";
import type { ChatMentionDto } from "@/lib/api/generated/model/chatMentionDto";
import { ROW_HOVER } from "@/lib/card-hover";
import { recordSheetHref } from "@/lib/record-sheet";
import { cn } from "@/lib/utils";

const CHIP = "inline-flex max-w-full items-center gap-1.5 rounded-full border px-2.5 py-0.5 text-xs outline-none focus-visible:ring-2 focus-visible:ring-ring";

/**
 * The records (people and organizations too) and emails an answer is about, as links: a record opens in the sidebar
 * over the chat, an email in the inbox.
 */
export function MentionChips({ mentions }: { mentions: readonly ChatMentionDto[] }) {
  const pathname = usePathname();
  const params = useSearchParams();
  if (mentions.length === 0) return null;
  return (
    <ul aria-label="Mentioned" className="flex flex-wrap gap-1.5">
      {mentions.map((mention) => {
        const record = mention.type === "RECORD" && mention.collectionId !== undefined && mention.recordId !== undefined;
        const href = record
          ? recordSheetHref(pathname, new URLSearchParams(params.toString()), mention.collectionId!, mention.recordId!)
          : `/inbox?email=${mention.emailId}`;
        const Icon = record ? FileText : Mail;
        return (
          <li key={record ? `r-${mention.collectionId}-${mention.recordId}` : `e-${mention.emailId}`} className="min-w-0">
            <Link href={href} className={cn(CHIP, ROW_HOVER)}>
              <Icon className="size-3.5 shrink-0 text-muted-foreground" strokeWidth={1.75} aria-hidden="true" />
              <span className="truncate">{mention.label}</span>
              {mention.collectionName ? <span className="shrink-0 text-muted-foreground">{mention.collectionName}</span> : null}
            </Link>
          </li>
        );
      })}
    </ul>
  );
}
