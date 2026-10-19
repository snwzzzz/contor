"use client";

import { useRouter } from "next/navigation";
import type { FormEvent } from "react";
import { SearchField } from "@/components/app/SearchField";

/** Mail search in the top bar; results are shown in the inbox. */
export function GlobalSearch() {
  const router = useRouter();

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const query = String(new FormData(event.currentTarget).get("q") ?? "").trim();
    if (query) router.push(`/inbox?${new URLSearchParams({ q: query })}`);
  }

  return (
    <form role="search" onSubmit={handleSubmit} className="hidden w-full max-w-sm md:block">
      <SearchField name="q" aria-label="Search mail" placeholder="Search mail…" />
    </form>
  );
}
