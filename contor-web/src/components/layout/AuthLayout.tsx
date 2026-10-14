import type { ReactNode } from "react";

/** Centered frame for login and setup. */
export function AuthLayout({ children }: { children: ReactNode }) {
  return (
    <main className="flex min-h-svh flex-col items-center justify-center gap-6 bg-muted/40 p-6">
      <p className="text-lg font-semibold tracking-tight">contor</p>
      {children}
    </main>
  );
}
