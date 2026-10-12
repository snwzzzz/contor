import { Archive, CalendarPlus, FolderPlus, Link2, ListPlus, PencilLine, Reply, Sparkles, Tag, type LucideIcon } from "lucide-react";
import type { ProposalDto } from "@/lib/api/generated/model/proposalDto";
import type { Tint } from "@/lib/collection-tint";

/** Filter groups of the proposals page, in the order of the chips. */
export const GROUPS = ["Calendar", "To-dos", "Categories", "Replies", "Labels", "Email", "Records", "Other"] as const;
export type Group = (typeof GROUPS)[number];

export interface Description {
  label: string;
  icon: LucideIcon;
  group: Group;
  tint?: Tint;
}

/** What the action does, in words, with its icon. Record actions depend on the target collection's slug. */
export function describeProposal(proposal: ProposalDto, slug: string | undefined): Description {
  switch (proposal.kind) {
    case "CREATE_RECORD":
      return slug === "events" ? { label: "Add to calendar", icon: CalendarPlus, group: "Calendar", tint: 1 }
        : slug === "tasks" ? { label: "New to-do", icon: ListPlus, group: "To-dos", tint: 3 }
          : { label: "New record", icon: ListPlus, group: "Records" };
    case "UPDATE_RECORD":
      return slug === "events" ? { label: "Change calendar entry", icon: PencilLine, group: "Calendar", tint: 1 }
        : slug === "tasks" ? { label: "Update to-do", icon: PencilLine, group: "To-dos", tint: 3 }
          : { label: "Update record", icon: PencilLine, group: "Records" };
    case "CREATE_COLLECTION":
      return { label: "New category", icon: FolderPlus, group: "Categories", tint: 6 };
    case "DRAFT_REPLY":
      return { label: "Draft reply", icon: Reply, group: "Replies", tint: 5 };
    case "SET_LABEL":
    case "CREATE_LABEL":
      return { label: proposal.kind === "SET_LABEL" ? "Label email" : "New label", icon: Tag, group: "Labels", tint: 2 };
    case "ARCHIVE_EMAIL":
      return { label: "Archive email", icon: Archive, group: "Email" };
    case "CREATE_RELATION":
      return { label: "Link records", icon: Link2, group: "Records" };
    default:
      return { label: "Suggestion", icon: Sparkles, group: "Other" };
  }
}
