"use client";

import { SendHorizontal, Sparkles } from "lucide-react";
import { useState, type FormEvent, type KeyboardEvent } from "react";
import { AppButton } from "@/components/app/AppButton";
import { Textarea } from "@/components/ui/textarea";
import { cn } from "@/lib/utils";

/** The backend's limit for a request or question. */
const MAX_PROMPT = 500;

interface ReportPromptProps {
  label: string;
  placeholder: string;
  submitLabel: string;
  /** Ready-made requests shown as chips; a click fills the input. */
  suggestions?: readonly string[];
  /** Whether an empty input may be sent (the backend then asks for its default summary). */
  allowEmpty?: boolean;
  /** Why asking is not possible right now; disables the input. */
  disabledReason?: string;
  pending?: boolean;
  onSubmit: (prompt: string) => void;
}

/** A small chat-style input for asking the assistant: Enter sends, Shift+Enter starts a new line. */
export function ReportPrompt({ label, placeholder, submitLabel, suggestions = [], allowEmpty = false, disabledReason, pending = false,
  onSubmit }: ReportPromptProps) {
  const [prompt, setPrompt] = useState("");
  const disabled = disabledReason !== undefined;
  const canSend = !disabled && !pending && (allowEmpty || prompt.trim() !== "");

  function send() {
    if (!canSend) return;
    onSubmit(prompt.trim());
    setPrompt("");
  }

  function onFormSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    send();
  }

  function onKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === "Enter" && !event.shiftKey && !event.nativeEvent.isComposing) {
      event.preventDefault();
      send();
    }
  }

  return (
    <form onSubmit={onFormSubmit} className="grid gap-2 rounded-xl border bg-card p-3">
      <div className="flex items-end gap-2">
        <Sparkles className="mb-2.5 size-4 shrink-0 text-primary" aria-hidden="true" />
        <Textarea
          aria-label={label}
          placeholder={placeholder}
          value={prompt}
          maxLength={MAX_PROMPT}
          rows={1}
          disabled={disabled}
          onChange={(event) => setPrompt(event.target.value)}
          onKeyDown={onKeyDown}
          className="min-h-9 resize-none border-0 bg-transparent px-1 shadow-none focus-visible:ring-0 dark:bg-transparent"
        />
        <AppButton type="submit" size="sm" loading={pending} disabled={!canSend} aria-label={submitLabel}>
          <SendHorizontal aria-hidden="true" />
        </AppButton>
      </div>
      {suggestions.length > 0 ? (
        <div className="flex flex-wrap gap-1.5 pl-6">
          {suggestions.map((suggestion) => (
            <button
              key={suggestion}
              type="button"
              disabled={disabled}
              onClick={() => setPrompt(suggestion)}
              className={cn("rounded-full border px-2.5 py-0.5 text-xs text-muted-foreground transition-colors hover:bg-muted hover:text-foreground",
                "disabled:pointer-events-none disabled:opacity-50")}
            >
              {suggestion}
            </button>
          ))}
        </div>
      ) : null}
      {disabledReason ? <p className="pl-6 text-xs text-muted-foreground">{disabledReason}</p> : null}
    </form>
  );
}
