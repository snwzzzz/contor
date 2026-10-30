"use client";

import { useGetCurrentUser } from "@/lib/api/generated/endpoints/auth/auth";
import { useTimeOfDay } from "@/lib/hooks/useTimeOfDay";
import { greeting } from "@/lib/time-of-day";
import { StatusBanner } from "./StatusBanner";

/** Top of the home page: a personal greeting and, below it, the one state that matters most right now. */
export function HomeHero() {
  const { data: user } = useGetCurrentUser();
  const period = useTimeOfDay();

  return (
    <div className="grid gap-1.5">
      <h1 className="text-2xl font-semibold tracking-tight">{greeting(period, user?.username)}</h1>
      <StatusBanner />
    </div>
  );
}
