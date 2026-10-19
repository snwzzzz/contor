"use client";

import { Search } from "lucide-react";
import { useRouter } from "next/navigation";
import type { FormEvent } from "react";
import { Input } from "@/components/ui/input";

/** Mail search in the top bar; results are shown in the inbox. */
export function GlobalSearch() {
  const router = useRouter();

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const query = String(new FormData(event.currentTarget).get("q") ?? "").trim();
    if (query) router.push(`/inbox?${new URLSearchParams({ q: query })}`);
  }

  return (
    <form role="search" onSubmit={handleSubmit} className="relative hidden w-full max-w-sm md:block">
      <Search className="pointer-events-none absolute left-2.5 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden="true" />
      <Input type="search" name="q" aria-label="Search mail" placeholder="Search mail…" className="h-8 border-transparent bg-muted/50 pl-8 shadow-none focus-visible:bg-background" />
    </form>
  );
}
