import { PageHeaderSkeleton } from "@/components/app/LoadingSkeletons";
import { ChatSkeleton } from "@/components/app/chat/ChatSkeleton";

/** The chat's header, conversation list and the conversation while its route loads. */
export default function Loading() {
  return (
    <>
      <PageHeaderSkeleton commands={2} />
      <ChatSkeleton conversation />
    </>
  );
}
