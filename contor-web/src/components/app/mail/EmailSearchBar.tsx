"use client";

import { Building2, ChevronDown, Search, User, X } from "lucide-react";
import { useDeferredValue, useId, useState, type KeyboardEvent } from "react";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuLabel,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Input } from "@/components/ui/input";
import { useSuggestEmailSenders } from "@/lib/api/generated/endpoints/mail/mail";
import type { EmailFolder } from "@/lib/api/generated/model/emailFolder";
import type { EmailSearchField } from "@/lib/api/generated/model/emailSearchField";
import type { EmailSuggestionDto } from "@/lib/api/generated/model/emailSuggestionDto";
import { cn } from "@/lib/utils";

const FIELDS: readonly { field: EmailSearchField; label: string }[] = [
  { field: "ALL", label: "All" },
  { field: "PEOPLE", label: "People" },
  { field: "SUBJECT", label: "Subject" },
  { field: "CONTENT", label: "Content" },
];

function fieldLabel(field: EmailSearchField): string {
  return FIELDS.find((option) => option.field === field)?.label ?? "All";
}

/** Suggestions start with the second character: one letter matches nearly every sender. */
const MIN_SUGGEST_LENGTH = 2;

interface EmailSearchBarProps {
  text: string;
  field: EmailSearchField;
  onChange: (text: string, field: EmailSearchField) => void;
  /** Autocomplete senders and companies from the whole inbox (server); off for already loaded lists. */
  suggestions?: boolean;
  /** SENT suggests the people you wrote to instead of senders. */
  folder?: EmailFolder;
  placeholder?: string;
  className?: string;
}

/**
 * Compact search field: where to search (all, people, subject, content) is a small menu inside the field, next to the
 * clear button; optional sender/company autocomplete below it.
 */
export function EmailSearchBar({ text, field, onChange, suggestions = false, folder, placeholder, className }: EmailSearchBarProps) {
  const listboxId = useId();
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(-1);
  const deferred = useDeferredValue(text.trim());
  const wanted = suggestions && open && deferred.length >= MIN_SUGGEST_LENGTH;
  const { data } = useSuggestEmailSenders({ q: deferred, ...(folder === "SENT" ? { folder } : {}) }, { query: { enabled: wanted, staleTime: 60_000 } });
  const options = wanted ? (data ?? []) : [];
  const expanded = options.length > 0;

  function pick(suggestion: EmailSuggestionDto) {
    setOpen(false);
    setActive(-1);
    onChange(suggestion.value, "PEOPLE");
  }

  function onKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === "Escape") {
      setOpen(false);
      setActive(-1);
    } else if (expanded && event.key === "ArrowDown") {
      event.preventDefault();
      setActive((index) => (index + 1) % options.length);
    } else if (expanded && event.key === "ArrowUp") {
      event.preventDefault();
      setActive((index) => (index <= 0 ? options.length - 1 : index - 1));
    } else if (expanded && event.key === "Enter" && active >= 0 && options[active]) {
      event.preventDefault();
      pick(options[active]);
    }
  }

  return (
    <div className={cn("relative min-w-0", className)}>
      <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden="true" />
      <Input
        role="combobox"
        aria-label="Search emails"
        aria-autocomplete={suggestions ? "list" : "none"}
        aria-expanded={expanded}
        aria-controls={expanded ? listboxId : undefined}
        aria-activedescendant={expanded && active >= 0 ? `${listboxId}-${active}` : undefined}
        value={text}
        placeholder={placeholder ?? "Search emails…"}
        autoComplete="off"
        onChange={(event) => {
          setOpen(true);
          setActive(-1);
          onChange(event.target.value, field);
        }}
        onFocus={() => setOpen(true)}
        onBlur={() => setOpen(false)}
        onKeyDown={onKeyDown}
        className={cn("h-9 pl-9", text ? "pr-32" : "pr-24")}
      />
      <div className="absolute top-1/2 right-1 flex -translate-y-1/2 items-center gap-0.5">
        {text ? (
          <button
            type="button"
            aria-label="Clear search"
            onClick={() => onChange("", field)}
            className="flex size-7 items-center justify-center rounded-md text-muted-foreground hover:bg-muted hover:text-foreground"
          >
            <X className="size-3.5" aria-hidden="true" />
          </button>
        ) : null}
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <button
              type="button"
              aria-label={`Search in: ${fieldLabel(field)}`}
              className="flex h-7 items-center gap-1 rounded-md px-2 text-xs font-medium text-muted-foreground hover:bg-muted hover:text-foreground"
            >
              {fieldLabel(field)}
              <ChevronDown className="size-3" aria-hidden="true" />
            </button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" className="w-40">
            <DropdownMenuLabel className="text-xs text-muted-foreground">Search in</DropdownMenuLabel>
            <DropdownMenuRadioGroup value={field} onValueChange={(value) => onChange(text, value as EmailSearchField)}>
              {FIELDS.map((option) => (
                <DropdownMenuRadioItem key={option.field} value={option.field}>
                  {option.label}
                </DropdownMenuRadioItem>
              ))}
            </DropdownMenuRadioGroup>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
      {expanded ? (
        <ul
          id={listboxId}
          role="listbox"
          aria-label="Suggestions"
          className="absolute top-full right-0 left-0 z-20 mt-1 grid gap-0.5 overflow-hidden rounded-lg border bg-popover p-1 text-popover-foreground shadow-md"
        >
          {options.map((option, index) => (
            <SuggestionOption
              key={`${option.kind}:${option.value}`}
              id={`${listboxId}-${index}`}
              suggestion={option}
              active={index === active}
              onPick={() => pick(option)}
            />
          ))}
        </ul>
      ) : null}
    </div>
  );
}

interface SuggestionOptionProps {
  id: string;
  suggestion: EmailSuggestionDto;
  active: boolean;
  onPick: () => void;
}

function SuggestionOption({ id, suggestion, active, onPick }: SuggestionOptionProps) {
  const company = suggestion.kind === "COMPANY";
  const Icon = company ? Building2 : User;
  return (
    <li
      id={id}
      role="option"
      aria-selected={active}
      // Keep the focus in the input so its blur does not close the list before the click lands.
      onMouseDown={(event) => event.preventDefault()}
      onClick={onPick}
      className={cn("flex cursor-pointer items-center gap-2.5 rounded-md px-2 py-1.5 text-sm", active ? "bg-accent text-accent-foreground" : "hover:bg-muted")}
    >
      <span className="flex size-6 shrink-0 items-center justify-center rounded-full bg-muted text-muted-foreground" aria-hidden="true">
        <Icon className="size-3.5" />
      </span>
      <span className="grid min-w-0 flex-1">
        <span className="truncate font-medium">{suggestion.label}</span>
        {company ? null : <span className="truncate text-xs text-muted-foreground">{suggestion.value}</span>}
      </span>
      <span className="shrink-0 text-xs text-muted-foreground">
        {company ? "Company" : "Person"} · {suggestion.emailCount}
      </span>
    </li>
  );
}
