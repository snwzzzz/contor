import type { MailAccountDto } from "@/lib/api/generated/model/mailAccountDto";
import { PictureAvatar } from "./PictureAvatar";

/** Profile picture of a connected mailbox (from its provider), or a tinted initial when it has none. */
export function AccountAvatar({ account, size = 20, className }: { account: MailAccountDto; size?: number; className?: string }) {
  return (
    <PictureAvatar
      label={account.displayName || account.emailAddress}
      src={account.hasPhoto ? `/api/mail/accounts/${account.id}/photo` : undefined}
      size={size}
      className={className}
    />
  );
}
