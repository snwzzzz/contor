"use client";

import { CircleAlert, LoaderCircle, RefreshCw } from "lucide-react";
import { AppButton } from "@/components/app/AppButton";
import type { ChatMessageDto } from "@/lib/api/generated/model/chatMessageDto";
import { chatStep, isAnswering } from "@/lib/chat";
import { ReportBlocks } from "../reports/ReportBody";
import { MentionChips } from "./MentionChips";

interface ChatAnswerProps {
  answer: ChatMessageDto;
  selected: number | undefined;
  onSelect: (marker: number) => void;
  /** Offered when the answer failed and is the conversation's last message. */
  onRetry?: () => void;
  retrying?: boolean;
}

/** One answer: what the assistant is doing while it writes, why it failed, or the answer with its sources and links. */
export function ChatAnswer({ answer, selected, onSelect, onRetry, retrying = false }: ChatAnswerProps) {
  return (
    <section aria-label="Assistant's answer" className="grid min-w-0 grid-cols-[minmax(0,1fr)] gap-3">
      {isAnswering(answer.status) ? (
        <p role="status" className="flex items-center gap-2 text-sm text-muted-foreground">
          <LoaderCircle className="size-4 animate-spin motion-reduce:animate-none" aria-hidden="true" />
          {chatStep(answer.status)}
        </p>
      ) : answer.status === "FAILED" ? (
        <div className="flex flex-wrap items-center gap-3">
          <p role="alert" className="flex items-center gap-2 text-sm text-destructive">
            <CircleAlert className="size-4 shrink-0" aria-hidden="true" />
            {answer.error ?? "The answer could not be written."}
          </p>
          {onRetry ? (
            <AppButton variant="outline" size="xs" loading={retrying} onClick={onRetry}>
              <RefreshCw aria-hidden="true" />
              Try again
            </AppButton>
          ) : null}
        </div>
      ) : (
        <>
          <ReportBlocks blocks={answer.blocks} sources={answer.sources} selected={selected} onSelect={onSelect} />
          <MentionChips mentions={answer.mentions} />
        </>
      )}
    </section>
  );
}
