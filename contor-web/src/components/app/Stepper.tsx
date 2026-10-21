"use client";

import { useState } from "react";
import { cn } from "@/lib/utils";

/** How long one bar takes to fill or empty; when several change, each waits for the one before. */
const BAR_MS = 150;

interface StepperProps {
  /** Accessible name of the step list. */
  label: string;
  steps: readonly string[];
  /** Index of the current step. */
  current: number;
  /** When given, finished steps are buttons that go back to them. */
  onSelect?: (index: number) => void;
}

type StepState = "complete" | "current" | "upcoming";

/**
 * When a bar starts to move. The bars that change between `from` and `current` go one after another, each once the one
 * before has finished, always starting at the step the user leaves: forward they fill left to right (`from + 1` first),
 * back they empty right to left (bar `from` first, the one next to the target last). E.g. from step 5 back to step 1:
 * bars 5, 4, 3, 2 at 0, 1, 2, 3 × BAR_MS.
 */
function barDelay(index: number, from: number, current: number): number {
  if (current > from && index > from && index <= current) return (index - from - 1) * BAR_MS;
  if (current < from && index > current && index <= from) return (from - index) * BAR_MS;
  return 0;
}

function stateOf(index: number, current: number): StepState {
  return index < current ? "complete" : index === current ? "current" : "upcoming";
}

/**
 * Horizontal progress through steps: a numbered label above a bar per step. Finished and current bars fill (animated,
 * left to right) in the primary color, the current label is highlighted, upcoming steps stay muted. With `onSelect`,
 * finished steps can be clicked to go back.
 */
export function Stepper({ label, steps, current, onSelect }: StepperProps) {
  // The step before the last change, kept while rendering (React's "previous props" pattern), for the stagger.
  const [shown, setShown] = useState(current);
  const [from, setFrom] = useState(current);
  if (shown !== current) {
    setFrom(shown);
    setShown(current);
  }

  return (
    <ol aria-label={label} className="grid gap-3" style={{ gridTemplateColumns: `repeat(${steps.length}, minmax(0, 1fr))` }}>
      {steps.map((step, index) => {
        const state = stateOf(index, current);
        const filled = state !== "upcoming";
        const clickable = onSelect !== undefined && state === "complete";
        return (
          // The label and the bar stay the same elements in every state (only classes change), so jumping back several
          // steps animates every bar; a finished step gets a button laid over it.
          <li
            key={step}
            data-state={state}
            aria-current={state === "current" ? "step" : undefined}
            className="group relative grid min-w-0 gap-2 rounded-sm has-focus-visible:ring-2 has-focus-visible:ring-ring has-focus-visible:ring-offset-2"
          >
            <span
              className={cn(
                "truncate text-xs transition-colors duration-300",
                state === "current" && "font-medium text-primary",
                state === "complete" && "text-muted-foreground",
                state === "upcoming" && "text-muted-foreground/60",
                clickable && "group-hover:text-foreground",
              )}
            >
              <span data-testid="step-number" className="mr-1.5 tabular-nums">
                {index + 1}
              </span>
              {step}
              {state === "complete" ? <span className="sr-only"> completed</span> : null}
            </span>
            <span className="h-1 overflow-hidden rounded-full bg-muted" aria-hidden="true">
              <span
                data-testid="step-fill"
                data-filled={filled}
                className={cn(
                  "block h-full origin-left rounded-full bg-primary transition-transform ease-out motion-reduce:transition-none",
                  filled ? "scale-x-100" : "scale-x-0",
                )}
                style={{ transitionDuration: `${BAR_MS}ms`, transitionDelay: `${barDelay(index, from, current)}ms` }}
              />
            </span>
            {clickable ? (
              <button type="button" onClick={() => onSelect(index)} className="absolute inset-0 cursor-pointer outline-none">
                <span className="sr-only">
                  Back to step {index + 1}: {step}
                </span>
              </button>
            ) : null}
          </li>
        );
      })}
    </ol>
  );
}
