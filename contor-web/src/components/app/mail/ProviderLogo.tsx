import type { MailProviderKind } from "@/lib/api/generated/model/mailProviderKind";
import { cn } from "@/lib/utils";

/**
 * Small brand mark of a mail provider (the Google "G", an Outlook tile), for the add account menu. Colors come from the
 * brand tokens in tokens.css; providers without a mark render nothing.
 */
export function ProviderLogo({ kind, className }: { kind: MailProviderKind; className?: string }) {
  if (kind === "GMAIL") {
    return (
      <svg viewBox="0 0 48 48" aria-hidden="true" data-provider-logo={kind} className={cn("size-4 shrink-0", className)}>
        <path
          className="fill-brand-google-blue"
          d="M43.6 20.1H42V20H24v8h11.3c-.8 2.2-2.2 4.2-4.1 5.6l6.2 5.2C37 39.2 44 34 44 24c0-1.3-.1-2.6-.4-3.9z"
        />
        <path
          className="fill-brand-google-green"
          d="M24 44c5.2 0 9.9-2 13.4-5.2l-6.2-5.2c-2 1.5-4.5 2.4-7.2 2.4-5.2 0-9.6-3.3-11.3-7.9l-6.5 5C9.5 39.6 16.2 44 24 44z"
        />
        <path
          className="fill-brand-google-yellow"
          d="M12.7 28.1c-.4-1.3-.7-2.7-.7-4.1s.3-2.8.7-4.1l-6.6-4.8C4.8 17.8 4 20.8 4 24s.8 6.2 2.2 8.9l6.5-4.8z"
        />
        <path
          className="fill-brand-google-red"
          d="M24 12c3.1 0 5.8 1.2 8 3l5.7-5.7C34 6.1 29.3 4 24 4 16.3 4 9.7 8.3 6.3 14.7l6.6 4.8C14.7 15.1 19 12 24 12z"
        />
      </svg>
    );
  }
  if (kind === "MICROSOFT_GRAPH") {
    return (
      <svg viewBox="0 0 48 48" aria-hidden="true" data-provider-logo={kind} className={cn("size-4 shrink-0", className)}>
        {/* the envelope behind, the "O" tile in front */}
        <rect className="fill-brand-outlook" x="17" y="9" width="27" height="30" rx="3" />
        <path className="fill-brand-outlook-dark" d="M17 22l13.5 8L44 22v14a3 3 0 0 1-3 3H20a3 3 0 0 1-3-3z" />
        <rect className="fill-brand-outlook-dark" x="4" y="12" width="24" height="24" rx="3" />
        <path
          className="fill-brand-outlook-foreground"
          d="M16 17.5c-3.7 0-6.2 2.8-6.2 6.5s2.5 6.5 6.2 6.5 6.2-2.8 6.2-6.5-2.5-6.5-6.2-6.5zm0 10.2c-1.8 0-3-1.5-3-3.7s1.2-3.7 3-3.7 3 1.5 3 3.7-1.2 3.7-3 3.7z"
        />
      </svg>
    );
  }
  return null;
}
