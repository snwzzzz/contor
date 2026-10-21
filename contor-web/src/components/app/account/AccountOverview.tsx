"use client";

import { LogOut } from "lucide-react";
import { useId } from "react";
import { AppButton } from "@/components/app/AppButton";
import { SettingsRow } from "@/components/app/settings/SettingsRow";
import { useGetCurrentUser } from "@/lib/api/generated/endpoints/auth/auth";
import type { Role } from "@/lib/api/generated/model/role";
import { useLogoutAndRedirect } from "@/lib/hooks/useLogoutAndRedirect";

const ROLE_LABEL: Record<Role, string> = { OWNER: "Owner", USER: "User" };

/** Settings → My account: who is signed in (username, email address, role) as plain rows, and Log out. Editing comes later. */
export function AccountOverview() {
  const { data: user } = useGetCurrentUser();
  const logout = useLogoutAndRedirect();
  const usernameId = useId();
  const emailId = useId();
  const roleId = useId();

  return (
    <div className="grid gap-8">
      <div className="grid divide-y">
        <SettingsRow title="Username" hint="The name you sign in with." labelId={usernameId}>
          <span aria-labelledby={usernameId} className="text-sm">
            {user?.username ?? "…"}
          </span>
        </SettingsRow>
        <SettingsRow title="Email address" hint="Where contor reaches you." labelId={emailId}>
          <span aria-labelledby={emailId} className="text-sm">
            {user?.email ?? "…"}
          </span>
        </SettingsRow>
        <SettingsRow title="Role" hint="Owners manage mailboxes, the AI and the collections." labelId={roleId}>
          <span aria-labelledby={roleId} className="text-sm">
            {user ? ROLE_LABEL[user.role] : "…"}
          </span>
        </SettingsRow>
      </div>
      <AppButton variant="outline" className="w-fit" loading={logout.isPending} onClick={() => logout.mutate()}>
        <LogOut aria-hidden="true" data-icon="inline-start" />
        Log out
      </AppButton>
    </div>
  );
}
