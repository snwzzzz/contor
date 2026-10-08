import { cn } from "@/lib/utils";

interface StepperProps {
  /** Accessible name of the step list. */
  label: string;
  steps: readonly string[];
  /** Index of the current step. */
  current: number;
}

type StepState = "complete" | "current" | "upcoming";

function stateOf(index: number, current: number): StepState {
  return index < current ? "complete" : index === current ? "current" : "upcoming";
}

/**
 * Horizontal progress through steps: a label above a bar per step. Finished and current bars fill (animated, left to
 * right) in the success color, the current label is highlighted, upcoming steps stay muted.
 */
export function Stepper({ label, steps, current }: StepperProps) {
  return (
    <ol aria-label={label} className="grid gap-3" style={{ gridTemplateColumns: `repeat(${steps.length}, minmax(0, 1fr))` }}>
      {steps.map((step, index) => {
        const state = stateOf(index, current);
        const filled = state !== "upcoming";
        return (
          <li key={step} data-state={state} aria-current={state === "current" ? "step" : undefined} className="grid min-w-0 gap-2">
            <span
              className={cn(
                "truncate text-xs transition-colors duration-300",
                state === "current" && "font-medium text-success",
                state === "complete" && "text-muted-foreground",
                state === "upcoming" && "text-muted-foreground/60",
              )}
            >
              {step}
              {state === "complete" ? <span className="sr-only"> completed</span> : null}
            </span>
            <span className="h-1 overflow-hidden rounded-full bg-muted" aria-hidden="true">
              <span
                data-testid="step-fill"
                data-filled={filled}
                className={cn(
                  "block h-full origin-left rounded-full bg-success transition-transform duration-500 ease-out motion-reduce:transition-none",
                  filled ? "scale-x-100" : "scale-x-0",
                )}
                style={{ transitionDelay: state === "current" ? "150ms" : "0ms" }}
              />
            </span>
          </li>
        );
      })}
    </ol>
  );
}
