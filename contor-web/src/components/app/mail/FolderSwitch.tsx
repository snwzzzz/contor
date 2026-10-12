"use client";

import type { EmailFolder } from "@/lib/api/generated/model/emailFolder";
import { cn } from "@/lib/utils";

const FOLDERS: readonly { value: EmailFolder; label: string }[] = [
  { value: "INBOX", label: "Inbox" },
  { value: "SENT", label: "Sent" },
];

interface FolderSwitchProps {
  folder: EmailFolder;
  onChange: (folder: EmailFolder) => void;
}

/** Inbox | Sent, a small segmented control above the message list. */
export function FolderSwitch({ folder, onChange }: FolderSwitchProps) {
  return (
    <div role="radiogroup" aria-label="Folder" className="inline-flex w-fit rounded-lg bg-muted p-0.5">
      {FOLDERS.map((entry) => (
        <button
          key={entry.value}
          type="button"
          role="radio"
          aria-checked={folder === entry.value}
          onClick={() => onChange(entry.value)}
          className={cn(
            "rounded-md px-3 py-1 text-sm transition-colors",
            folder === entry.value ? "bg-background font-medium shadow-sm" : "text-muted-foreground hover:text-foreground",
          )}
        >
          {entry.label}
        </button>
      ))}
    </div>
  );
}
