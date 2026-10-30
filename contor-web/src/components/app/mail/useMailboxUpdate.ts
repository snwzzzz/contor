"use client";

import { useQueryClient } from "@tanstack/react-query";
import { getListEmailsQueryKey, getListMailAccountsQueryKey, useUpdateMailAccount } from "@/lib/api/generated/endpoints/mail/mail";
import type { MailAccountDto } from "@/lib/api/generated/model/mailAccountDto";

/**
 * Changes a mailbox's flags (read by the AI, hidden in contor): the mailbox list shows the saved mailbox at once, and
 * the email lists load again, since hiding a mailbox changes what they show.
 */
export function useMailboxUpdate() {
  const queryClient = useQueryClient();
  return useUpdateMailAccount({
    mutation: {
      onSuccess: (saved) => {
        queryClient.setQueryData<MailAccountDto[]>(getListMailAccountsQueryKey(), (accounts) =>
          accounts?.map((account) => (account.id === saved.id ? saved : account)),
        );
        void queryClient.invalidateQueries({ queryKey: getListEmailsQueryKey() });
      },
    },
  });
}
