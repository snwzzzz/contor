"use client";

import { useQueryClient } from "@tanstack/react-query";
import { LoaderCircle, SquarePen } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { useGetAiStatus } from "@/lib/api/generated/endpoints/ai/ai";
import {
  getGetChatConversationQueryKey,
  getListChatConversationsQueryKey,
  useCreateChatConversation,
  useListChatConversations,
} from "@/lib/api/generated/endpoints/chat/chat";
import type { ChatConversationSummaryDto } from "@/lib/api/generated/model/chatConversationSummaryDto";
import { ROW_HOVER } from "@/lib/card-hover";
import { CHAT_POLL_MS, chatHref, MAX_QUESTION } from "@/lib/chat";
import { cn } from "@/lib/utils";
import { ReportPrompt } from "../reports/ReportPrompt";
import { REPORT_NOT_READY } from "../reports/ReportRow";
import { ChatListSkeleton } from "./ChatSkeleton";
import { ChatThread } from "./ChatThread";

const SUGGESTIONS = [
  "Who are our best customers?",
  "Which complaints are still open?",
  "What is on my calendar this week?",
  "Which to-dos are overdue?",
] as const;

const DATE = new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "short" });

const ROW = "flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-left text-sm outline-none focus-visible:ring-2 focus-visible:ring-ring";

/**
 * The chat page: a side list of your conversations (New chat, one row each) and, next to it, either the start (question,
 * input, ready-made questions; sending opens the new conversation) or the open conversation. From lg it is exactly the
 * rest of the screen: the list and the conversation scroll on their own.
 */
export function ChatWorkspace({ conversationId }: { conversationId?: number }) {
  return (
    <section
      aria-label="Chat"
      className="grid gap-6 lg:min-h-0 lg:flex-1 lg:grid-cols-[16rem_minmax(0,1fr)] lg:grid-rows-[minmax(0,1fr)] lg:gap-0"
    >
      <ConversationList current={conversationId} />
      {/* Scrolls on its own, never sideways; pb-6 keeps room for the sticky follow-up input at the bottom. */}
      <div
        data-testid="chat-detail"
        className="flex min-h-0 min-w-0 flex-col max-lg:order-first lg:overflow-x-hidden lg:overflow-y-auto lg:pt-4 lg:pb-6 lg:pl-6"
      >
        {conversationId === undefined ? <ChatStart /> : <ChatThread key={conversationId} conversationId={conversationId} />}
      </div>
    </section>
  );
}

/** A new conversation: the question line, the input and ready-made questions. */
function ChatStart() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const { data: status } = useGetAiStatus({ query: { retry: false } });
  const create = useCreateChatConversation({
    mutation: {
      onSuccess: (created) => {
        queryClient.setQueryData(getGetChatConversationQueryKey(created.id), created);
        void queryClient.invalidateQueries({ queryKey: getListChatConversationsQueryKey() });
        router.push(chatHref(created.id));
      },
    },
  });
  const disabledReason = status !== undefined && !status.ready ? REPORT_NOT_READY : undefined;
  const ask = (text: string) => create.mutate({ data: { text } });

  return (
    // Centered in its area, like a chat start page.
    <div className="mx-auto flex min-h-80 w-full max-w-3xl flex-1 flex-col justify-center gap-3 pb-[10vh]">
      <h2 className="text-center text-xl font-semibold tracking-tight">What do you want to know?</h2>
      <p className="text-center text-sm text-muted-foreground">
        Ask about your collections, people, connections, mail, calendar and to-dos. Answers link to what they are about.
      </p>
      <ReportPrompt
        label="Ask the assistant"
        placeholder="Ask anything about your data…"
        submitLabel="Ask"
        maxLength={MAX_QUESTION}
        disabledReason={disabledReason}
        pending={create.isPending}
        onSubmit={ask}
      />
      <ul aria-label="Suggestions" className="flex flex-wrap justify-center gap-2">
        {SUGGESTIONS.map((suggestion) => (
          <li key={suggestion}>
            <Button
              variant="outline"
              size="sm"
              className="font-normal shadow-none"
              disabled={disabledReason !== undefined || create.isPending}
              onClick={() => ask(suggestion)}
            >
              {suggestion}
            </Button>
          </li>
        ))}
      </ul>
    </div>
  );
}

/** The side list: New chat and one plain row per conversation, most recent first (polled while one is answered). */
function ConversationList({ current }: { current: number | undefined }) {
  const { data: conversations } = useListChatConversations({
    query: { retry: false, refetchInterval: (query) => (query.state.data?.some((conversation) => conversation.answering) ? CHAT_POLL_MS : false) },
  });

  return (
    <aside aria-label="Conversations" className="min-h-0 min-w-0 lg:h-full lg:border-r">
      <div className="flex h-full min-h-0 flex-col gap-2 lg:pt-4 lg:pr-4">
        <div className="flex h-8 items-center gap-1">
          <h3 className="mr-auto text-sm font-medium">Conversations</h3>
          <Button asChild variant="ghost" size="icon-sm" className="text-muted-foreground">
            <Link href="/chat" aria-label="New chat" title="New chat">
              <SquarePen aria-hidden="true" />
            </Link>
          </Button>
        </div>
        {conversations === undefined ? (
          <ChatListSkeleton />
        ) : conversations.length === 0 ? (
          <p className="px-2 py-4 text-xs text-muted-foreground">No conversations yet</p>
        ) : (
          <div className="-mx-2 min-h-0 flex-1 overflow-x-hidden overflow-y-auto px-2">
            <ul className="-mx-2 grid content-start">
              {conversations.map((conversation) => (
                <li key={conversation.id}>
                  <ConversationRow conversation={conversation} current={conversation.id === current} />
                </li>
              ))}
            </ul>
          </div>
        )}
      </div>
    </aside>
  );
}

/** One conversation as a plain row: its title, and on the right its date, or that it is still being answered. */
function ConversationRow({ conversation, current }: { conversation: ChatConversationSummaryDto; current: boolean }) {
  return (
    <Link
      href={chatHref(conversation.id)}
      aria-current={current ? "page" : undefined}
      className={cn(ROW, ROW_HOVER, current && "bg-accent font-medium hover:bg-accent")}
    >
      <span className="min-w-0 flex-1 truncate">{conversation.title}</span>
      {conversation.answering ? (
        <span className="flex shrink-0 items-center gap-1 text-xs font-normal text-muted-foreground">
          <LoaderCircle aria-hidden="true" className="size-3 animate-spin motion-reduce:animate-none" />
          Writing…
        </span>
      ) : (
        <time dateTime={conversation.lastModified ?? conversation.createdAt} className="shrink-0 text-xs font-normal text-muted-foreground tabular-nums">
          {DATE.format(new Date(conversation.lastModified ?? conversation.createdAt))}
        </time>
      )}
    </Link>
  );
}
