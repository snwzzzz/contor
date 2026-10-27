"use client";

import { useQueryClient } from "@tanstack/react-query";
import { Pencil, Trash2 } from "lucide-react";
import { useRouter } from "next/navigation";
import { useState, type FormEvent, type KeyboardEvent } from "react";
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
    <article className="flex min-w-0 flex-1 flex-col gap-6">
      <ThreadHeader
        title={conversation.title}
        renaming={rename.isPending}
        deleting={remove.isPending}
        onRename={(title) => rename.mutate({ conversationId, data: { title } })}
        onDelete={() => remove.mutate({ conversationId })}
      />

      <div className="grid flex-1 gap-8 lg:grid-cols-[minmax(0,1fr)_20rem]">
        {/* min-w-0: a wide table or a long word scrolls in its own box instead of widening the thread. */}
        <div className="flex min-w-0 flex-col gap-6 break-words">
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
          {/* Pushed to the bottom (mt-auto) and stays there while scrolling (-bottom-6 reaches past the scroll area's
              padding); the fade lets text pass under it. */}
          <div data-slot="chat-composer" className="sticky -bottom-6 z-10 -mb-6 mt-auto bg-linear-to-t from-background from-75% to-transparent pt-6 pb-6">
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
        <SourcePanel
          sources={shown?.sources ?? []}
          selected={shown && selection?.messageId === shown.id ? selection.marker : undefined}
          quotes={quotesOf(shown?.sources ?? [])}
          onSelect={shown ? select(shown.id) : () => {}}
        />
      </div>
    </article>
  );
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
