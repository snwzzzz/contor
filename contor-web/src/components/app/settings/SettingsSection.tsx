import { useId, type ReactNode } from "react";

/** A titled part of a settings page: a small heading over a hairline, no box (like the home page's sections). */
export function SettingsSection({ title, children }: { title: string; children: ReactNode }) {
  const id = useId();
  return (
    <section aria-labelledby={id} className="grid gap-4">
      <h3 id={id} className="flex h-8 items-center border-b text-sm font-medium">
        {title}
      </h3>
      {children}
    </section>
  );
}
