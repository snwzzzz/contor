"use client";

import { useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { useGetAiStatus } from "@/lib/api/generated/endpoints/ai/ai";
import {
  getGetChatConversationQueryKey,
  getListChatConversationsQueryKey,
  useCreateChatConversation,
} from "@/lib/api/generated/endpoints/chat/chat";
import { chatHref, MAX_QUESTION } from "@/lib/chat";
import { cn } from "@/lib/utils";
import { ReportPrompt } from "../reports/ReportPrompt";
import { REPORT_NOT_READY } from "../reports/ReportRow";

const SUGGESTIONS = [
  "Who are our best customers?",
  "Which complaints are still open?",
  "What is on my calendar this week?",
  "Which to-dos are overdue?",
] as const;

/**
 * Starting a conversation: `ask` sends the first question and opens the new conversation (its data already in the
 * cache, so it shows at once); `disabledReason` while the AI model is not ready.
 */
export function useStartChat() {
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
  return { ask: (text: string) => create.mutate({ data: { text } }), pending: create.isPending, disabledReason };
}

/** The input that starts a conversation (chat start page, home page). */
export function ChatAsk({ chat }: { chat: ReturnType<typeof useStartChat> }) {
  return (
    <ReportPrompt
      label="Ask the assistant"
      placeholder="Ask anything about your data…"
      submitLabel="Ask"
      maxLength={MAX_QUESTION}
      disabledReason={chat.disabledReason}
      pending={chat.pending}
      onSubmit={chat.ask}
    />
  );
}

/** Ready-made questions: one click sends one (disabled like the input). */
export function ChatSuggestions({ chat, className }: { chat: ReturnType<typeof useStartChat>; className?: string }) {
  return (
    <ul aria-label="Suggestions" className={cn("flex flex-wrap gap-2", className)}>
      {SUGGESTIONS.map((suggestion) => (
        <li key={suggestion}>
          <Button
            variant="outline"
            size="sm"
            className="font-normal shadow-none"
            disabled={chat.disabledReason !== undefined || chat.pending}
            onClick={() => chat.ask(suggestion)}
          >
            {suggestion}
          </Button>
        </li>
      ))}
    </ul>
  );
}
