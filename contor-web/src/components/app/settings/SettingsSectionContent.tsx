import { ShieldCheck } from "lucide-react";
import { AiAutonomySettings } from "@/components/app/ai/AiAutonomySettings";
import { AiSettingsPanel } from "@/components/app/ai/AiSettingsPanel";
import { EmptyState } from "@/components/app/EmptyState";
import { AccountsList } from "@/components/app/mail/AccountsList";
import { AccountOverview } from "@/components/app/account/AccountOverview";
import { GeneralSettings } from "./GeneralSettings";
import type { SettingsSection } from "@/lib/navigation";

interface SettingsSectionContentProps {
  section: SettingsSection;
  /** Result of the mail provider OAuth callback, forwarded into the accounts section. */
  connected?: boolean;
  mailError?: string;
}

/** Body of one settings section inside the settings modal. */
export function SettingsSectionContent({ section, connected, mailError }: SettingsSectionContentProps) {
  switch (section) {
    case "account":
      return <AccountOverview />;
    case "general":
      return <GeneralSettings />;
    case "accounts":
      return <AccountsList connected={connected} mailError={mailError} />;
    case "ai":
      return <AiSettingsPanel />;
    case "autonomy":
      return <AiAutonomySettings />;
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
