import { CircleCheck, CircleDashed } from "lucide-react";
import type { ReplyQuestion, ReplySlot } from "@/lib/reply-draft";

const SLOT = new Intl.DateTimeFormat("en-GB", { weekday: "short", day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" });
const SLOT_END = new Intl.DateTimeFormat("en-GB", { hour: "2-digit", minute: "2-digit" });

interface ReplyQuestionsProps {
  questions: readonly ReplyQuestion[];
  slots: readonly ReplySlot[];
}

/**
 * What a reply draft covers: the questions of the email with whether the draft answers them (open ones are left as a
 * placeholder), and the free times from the calendar it offers. Nothing when there is neither.
 */
export function ReplyQuestions({ questions, slots }: ReplyQuestionsProps) {
  if (questions.length === 0 && slots.length === 0) return null;
  return (
    <div className="grid gap-2 text-xs">
      {questions.length > 0 ? (
        <ul aria-label="Questions in the email" className="grid gap-1">
          {questions.map(({ question, answered }) => (
            <li key={question} className="flex items-start gap-1.5">
              {answered === true ? (
                <CircleCheck aria-label="Answered" className="mt-px size-3.5 shrink-0 text-success" />
              ) : answered === false ? (
                <CircleDashed aria-label="Still open: fill in the placeholder" className="mt-px size-3.5 shrink-0 text-warning" />
              ) : (
                <span className="size-3.5 shrink-0" aria-hidden="true" />
              )}
              <span className="text-muted-foreground">{question}</span>
            </li>
          ))}
        </ul>
      ) : null}
      {slots.length > 0 ? (
        <ul aria-label="Free times offered" className="flex flex-wrap gap-1.5">
          {slots.map((slot) => (
            <li key={slot.start} className="rounded-full border px-2 py-0.5 tabular-nums">
              {SLOT.format(new Date(slot.start))}–{SLOT_END.format(new Date(slot.end))}
            </li>
          ))}
        </ul>
      ) : null}
    </div>
  );
}
