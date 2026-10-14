import type { RecordDto } from "@/lib/api/generated/model/recordDto";
import type { DueTone } from "@/lib/due";

export type State = "open" | "in_progress" | "done";

export const DUE_TONE: Record<DueTone, string> = {
  overdue: "text-destructive",
  today: "text-tint-2-vivid",
  soon: "text-foreground",
  later: "text-muted-foreground",
};

export const PRIORITY: Record<string, { label: string; className: string; rank: number }> = {
  high: { label: "High", className: "bg-destructive/15 text-destructive", rank: 0 },
  normal: { label: "Normal", className: "bg-muted text-muted-foreground", rank: 1 },
  low: { label: "Low", className: "bg-info/15 text-info", rank: 2 },
};

export function stateOf(task: RecordDto): State {
  const state = task.values.state;
  return state === "in_progress" || state === "done" ? state : "open";
}

export function stringValue(task: RecordDto, key: string): string | null {
  const value = task.values[key];
  return typeof value === "string" && value ? value : null;
}
