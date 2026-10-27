"use client";

import { ChatAsk, ChatSuggestions, useStartChat } from "../chat/ChatAsk";
import { HomeSection, SectionLink } from "./HomeSection";

/** The chat on the home page, full width: a line of text, the input and ready-made questions; sending opens the new conversation. */
export function HomeChat() {
  const chat = useStartChat();

  return (
    <HomeSection
      title="What do you want to know?"
      action={
        <SectionLink href="/chat" arrow>
          All chats
        </SectionLink>
      }
      className="gap-3"
    >
      <p className="text-sm text-muted-foreground">
        Ask the assistant about your collections, people, connections, mail, calendar and to-dos. Answers link to what they
        are about.
      </p>
      <ChatAsk chat={chat} />
      <ChatSuggestions chat={chat} />
    </HomeSection>
  );
}
