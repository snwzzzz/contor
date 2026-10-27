import type { ChatConversationDto } from "@/lib/api/generated/model/chatConversationDto";
import type { ChatMessageStatus } from "@/lib/api/generated/model/chatMessageStatus";
import type { ReportSourceDto } from "@/lib/api/generated/model/reportSourceDto";

/** How often a conversation whose answer is still being written is fetched again. */
export const CHAT_POLL_MS = 2000;

/** The backend's limit for a question. */
export const MAX_QUESTION = 2000;

const ANSWERING: ReadonlySet<ChatMessageStatus> = new Set<ChatMessageStatus>(["PENDING", "RETRIEVING", "WRITING"]);

/** What the assistant is doing in each state, for the progress line. */
const CHAT_STEP: Record<ChatMessageStatus, string> = {
  PENDING: "Waiting for the assistant…",
  RETRIEVING: "Looking up records and emails…",
  WRITING: "Writing the answer…",
  DONE: "Done",
  FAILED: "Failed",
};

export function chatStep(status: ChatMessageStatus): string {
  return CHAT_STEP[status];
}

export function isAnswering(status: ChatMessageStatus): boolean {
  return ANSWERING.has(status);
}

/** Whether an answer of the conversation is still being written (then it is polled and no new question can be asked). */
export function conversationAnswering(conversation: ChatConversationDto | undefined): boolean {
  return conversation?.messages.some((message) => isAnswering(message.status)) ?? false;
}

export function chatHref(conversationId: number): string {
  return `/chat/${conversationId}`;
}

/** The cited text per marker, highlighted in its email paragraph in the sources panel. */
export function quotesOf(sources: readonly ReportSourceDto[]): Map<number, string> {
  const quotes = new Map<number, string>();
  for (const source of sources) {
    if (source.quote) quotes.set(source.marker, source.quote);
  }
  return quotes;
}
