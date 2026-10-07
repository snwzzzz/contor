import { LoaderCircle } from "lucide-react";
import type { ComponentProps } from "react";
import { Button } from "@/components/ui/button";

export type AppButtonProps = ComponentProps<typeof Button> & {
  /** Shows a spinner, disables the button and marks it busy (e.g. while a request runs). */
  loading?: boolean;
};

/** The app's button. Pages use this instead of the shadcn primitive so look and behavior stay in one place. */
export function AppButton({ loading = false, disabled, children, ...props }: AppButtonProps) {
  return (
    <Button disabled={disabled || loading} aria-busy={loading || undefined} {...props}>
      {loading ? <LoaderCircle className="animate-spin" aria-hidden="true" data-icon="inline-start" /> : null}
      {children}
    </Button>
  );
}
