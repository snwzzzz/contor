import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { ChatHeader } from "@/components/app/chat/ChatHeader";
import { ChatWorkspace } from "@/components/app/chat/ChatWorkspace";

export const metadata: Metadata = { title: "Chat" };

function positiveId(value: string): number | null {
  const id = Number(value);
  return Number.isInteger(id) && id > 0 ? id : null;
}

export default async function ChatConversationPage({ params }: PageProps<"/chat/[conversationId]">) {
  const conversationId = positiveId((await params).conversationId);
  if (conversationId === null) notFound();
  return (
    <>
      <ChatHeader />
      <ChatWorkspace conversationId={conversationId} />
    </>
  );
}
