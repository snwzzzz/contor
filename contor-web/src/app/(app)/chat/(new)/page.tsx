import type { Metadata } from "next";
import { ChatHeader } from "@/components/app/chat/ChatHeader";
import { ChatWorkspace } from "@/components/app/chat/ChatWorkspace";

export const metadata: Metadata = { title: "Chat" };

export default function Page() {
  return (
    <>
      <ChatHeader />
      <ChatWorkspace />
    </>
  );
}
