"use client";

import { useQueryClient } from "@tanstack/react-query";
import { Pencil, Trash2 } from "lucide-react";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useRef, useState, type FormEvent, type KeyboardEvent } from "react";
import { AppButton } from "@/components/app/AppButton";
import { ErrorMessage } from "@/components/app/ErrorMessage";
import { Input } from "@/components/ui/input";
import { useGetAiStatus } from "@/lib/api/generated/endpoints/ai/ai";
import {
  getGetChatConversationQueryKey,
  getListChatConversationsQueryKey,
  useAskChatQuestion,
  useDeleteChatConversation,
  useGetChatConversation,
  useRetryChatMessage,
  useUpdateChatConversation,
} from "@/lib/api/generated/endpoints/chat/chat";
import type { ChatConversationDto } from "@/lib/api/generated/model/chatConversationDto";
import { CHAT_POLL_MS, conversationAnswering, MAX_QUESTION, quotesOf } from "@/lib/chat";
import { ReportPrompt } from "../reports/ReportPrompt";
import { REPORT_NOT_READY } from "../reports/ReportRow";
import { SourcePanel } from "../reports/SourcePanel";
import { ChatAnswer } from "./ChatAnswer";
import { ChatThreadSkeleton } from "./ChatSkeleton";

/** Which source is open: markers are numbered per answer. */
interface Selection {
  messageId: number;
  marker: number;
}

/**
 * One conversation: its title (rename, delete), each question as the user's message with the assistant's answer below
 * it, and the follow-up input at the bottom; next to it the sources of the selected (or the last) answer. Polled while an
 * answer is being written.
 */
export function ChatThread({ conversationId }: { conversationId: number }) {
  const router = useRouter();
  const queryClient = useQueryClient();
  const queryKey = getGetChatConversationQueryKey(conversationId);
  const { data: conversation, error } = useGetChatConversation(conversationId, {
    query: { retry: false, refetchInterval: (query) => (conversationAnswering(query.state.data) ? CHAT_POLL_MS : false) },
  });
  const { data: status } = useGetAiStatus({ query: { retry: false } });
  const [selection, setSelection] = useState<Selection>();
  const end = useStickToEnd(conversation?.messages.length ?? 0, conversation?.messages.at(-1)?.status);
  const show = (updated: ChatConversationDto) => {
    queryClient.setQueryData(queryKey, updated);
    void queryClient.invalidateQueries({ queryKey: getListChatConversationsQueryKey() });
  };
  const ask = useAskChatQuestion({ mutation: { onSuccess: show } });
  const retry = useRetryChatMessage({ mutation: { onSuccess: show } });
  const rename = useUpdateChatConversation({ mutation: { onSuccess: show } });
  const remove = useDeleteChatConversation({
    mutation: {
      onSuccess: async () => {
        queryClient.removeQueries({ queryKey });
        await queryClient.invalidateQueries({ queryKey: getListChatConversationsQueryKey() });
        router.push("/chat");
      },
    },
  });

  if (error) return <ErrorMessage message="This conversation does not exist (anymore)." />;
  if (!conversation) return <ChatThreadSkeleton />;

  const answering = conversationAnswering(conversation);
  const last = conversation.messages.at(-1);
  const answers = conversation.messages.filter((message) => message.role === "ASSISTANT");
  const shown = answers.find((answer) => answer.id === selection?.messageId) ?? answers.at(-1);
  const select = (messageId: number) => (marker: number) =>
    setSelection((current) => (current?.messageId === messageId && current.marker === marker ? undefined : { messageId, marker }));
  const disabledReason = answering ? "Wait until the answer is ready."
    : status !== undefined && !status.ready ? REPORT_NOT_READY : undefined;

  return (
    // Fills the detail area (a flex column), so the follow-up input sits at the bottom even while the thread is short.
    <article className="flex min-w-0 flex-1 flex-col gap-6 lg:min-h-0">
      <ThreadHeader
        title={conversation.title}
        renaming={rename.isPending}
        deleting={remove.isPending}
        onRename={(title) => rename.mutate({ conversationId, data: { title } })}
        onDelete={() => remove.mutate({ conversationId })}
      />

      <div className="grid flex-1 gap-8 lg:min-h-0 lg:grid-cols-[minmax(0,1fr)_20rem] lg:grid-rows-[minmax(0,1fr)]">
        <div className="flex min-w-0 flex-col lg:min-h-0">
          {/* The messages scroll on their own (from lg) above the input; min-w-0: a wide table or a long word scrolls in
              its own box instead of widening the thread. */}
          <div data-testid="chat-messages" className="flex min-w-0 flex-1 flex-col gap-6 break-words lg:min-h-0 lg:overflow-y-auto lg:pr-2">
            {conversation.messages.map((message) =>
              message.role === "USER" ? (
                <Question key={message.id} text={message.text ?? ""} />
              ) : (
                <ChatAnswer
                  key={message.id}
                  answer={message}
                  selected={selection?.messageId === message.id ? selection.marker : undefined}
                  onSelect={select(message.id)}
                  onRetry={message === last ? () => retry.mutate({ conversationId, messageId: message.id }) : undefined}
                  retrying={retry.isPending}
                />
              ),
            )}
            {/* The very end of the conversation: scrolled into view by useStickToEnd. */}
            <div ref={end} data-slot="chat-end" aria-hidden="true" className="-mt-6 h-px shrink-0" />
          </div>
          {/* Below the messages, never over them; on small screens the page scrolls and it stays at the bottom. */}
          <div data-slot="chat-composer" className="z-10 bg-background pt-3 max-lg:sticky max-lg:bottom-0 max-lg:pb-3">
            <ReportPrompt
              label="Ask a follow-up"
              placeholder="Ask a follow-up question…"
              submitLabel="Ask"
              maxLength={MAX_QUESTION}
              disabledReason={disabledReason}
              pending={ask.isPending}
              onSubmit={(text) => ask.mutate({ conversationId, data: { text } })}
            />
          </div>
        </div>
        {/* Long source lists scroll on their own beside the messages. */}
        <div className="min-w-0 lg:min-h-0 lg:overflow-y-auto">
          <SourcePanel
            sources={shown?.sources ?? []}
            selected={shown && selection?.messageId === shown.id ? selection.marker : undefined}
            quotes={quotesOf(shown?.sources ?? [])}
            onSelect={shown ? select(shown.id) : () => {}}
          />
        </div>
      </div>
    </article>
  );
}

/**
 * Keeps the end of the conversation in view, so the sticky input never covers the newest answer: on opening, when a
 * question is sent (more messages), and when the last answer changes (it finished, failed) while you are at the end.
 * Scrolled up to read, you stay where you are. Returns the ref for an element at the very end.
 */
function useStickToEnd(count: number, lastStatus: string | undefined) {
  const element = useRef<HTMLDivElement | null>(null);
  const atEnd = useRef(true);
  const shown = useRef(0);
  const ref = useCallback((node: HTMLDivElement | null) => {
    element.current = node;
    if (!node || typeof IntersectionObserver === "undefined") return;
    const observer = new IntersectionObserver(([entry]) => {
      atEnd.current = entry.isIntersecting;
    });
    observer.observe(node);
    return () => observer.disconnect();
  }, []);

  useEffect(() => {
    const grew = count > shown.current;
    shown.current = count;
    if (count > 0 && (grew || atEnd.current)) {
      element.current?.scrollIntoView({ block: "end" });
    }
  }, [count, lastStatus]);

  return ref;
}

interface ThreadHeaderProps {
  title: string;
  renaming: boolean;
  deleting: boolean;
  onRename: (title: string) => void;
  onDelete: () => void;
}

/** The conversation's title over a hairline, with Rename (in place) and Delete (after a confirmation). */
function ThreadHeader({ title, renaming, deleting, onRename, onDelete }: ThreadHeaderProps) {
  const [draft, setDraft] = useState<string>();
  const [confirmDelete, setConfirmDelete] = useState(false);

  function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const next = draft?.trim();
    if (next && next !== title) onRename(next);
    setDraft(undefined);
  }

  function onKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === "Escape") setDraft(undefined);
  }

  return (
    <header className="flex min-h-9 flex-wrap items-center gap-2 border-b pb-3">
      {draft === undefined ? (
        <>
          <h2 className="mr-auto min-w-0 text-xl font-semibold tracking-tight break-words">{title}</h2>
          <AppButton variant="ghost" size="sm" loading={renaming} onClick={() => setDraft(title)}>
            <Pencil aria-hidden="true" />
            Rename
          </AppButton>
        </>
      ) : (
        <form onSubmit={save} className="mr-auto flex min-w-0 flex-1 items-center gap-2">
          <Input aria-label="Title" value={draft} maxLength={120} autoFocus onKeyDown={onKeyDown}
            onChange={(event) => setDraft(event.target.value)} className="max-w-md" />
          <AppButton type="submit" size="sm" disabled={draft.trim() === ""}>Save</AppButton>
          <AppButton type="button" variant="ghost" size="sm" onClick={() => setDraft(undefined)}>Cancel</AppButton>
        </form>
      )}
      {confirmDelete ? (
        <>
          <AppButton variant="destructive" size="sm" loading={deleting} onClick={onDelete}>Delete conversation</AppButton>
          <AppButton variant="ghost" size="sm" onClick={() => setConfirmDelete(false)}>Cancel</AppButton>
        </>
      ) : (
        <AppButton variant="ghost" size="sm" onClick={() => setConfirmDelete(true)}>
          <Trash2 aria-hidden="true" />
          Delete
        </AppButton>
      )}
    </header>
  );
}

/** What the user asked, as their own message on the right. */
function Question({ text }: { text: string }) {
  return (
    <section aria-label="Your question" className="flex justify-end">
      <div className="max-w-[85%] min-w-0 rounded-2xl rounded-tr-sm bg-primary px-4 py-2.5 text-sm whitespace-pre-wrap break-words text-primary-foreground">
        {text}
      </div>
    </section>
  );
}
