import { Bot, Brain, ShieldCheck } from "lucide-react";
import { EmptyState } from "@/components/app/EmptyState";
import { AccountsList } from "@/components/app/mail/AccountsList";
import type { SettingsSection } from "@/lib/navigation";

interface SettingsSectionContentProps {
  section: SettingsSection;
  /** Result of the Microsoft OAuth callback, forwarded into the accounts section. */
  connected?: boolean;
  mailError?: string;
}

/** Body of one settings section inside the settings modal. */
export function SettingsSectionContent({ section, connected, mailError }: SettingsSectionContentProps) {
  switch (section) {
    case "accounts":
      return <AccountsList connected={connected} mailError={mailError} />;
    case "ai":
      return (
        <EmptyState
          icon={Brain}
          title="AI is not active yet"
          description="Model selection arrives with the AI pipeline in the next phase. Ollama runs locally and must stay bound to localhost."
        />
      );
    case "autonomy":
      return (
        <EmptyState
          icon={Bot}
          title="Everything is a suggestion"
          description="Until autonomy settings arrive, every AI action needs your approval."
        />
      );
    case "security":
      return (
        <EmptyState
          icon={ShieldCheck}
          title="Two-factor authentication coming soon"
          description="TOTP 2FA and recovery codes arrive in a later step."
        />
      );
  }
}
