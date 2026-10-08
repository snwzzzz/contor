"use client";

import { CalendarDays, ChevronLeft, ChevronRight } from "lucide-react";
import { useState } from "react";
import { AppButton } from "@/components/app/AppButton";
import { EmptyState } from "@/components/app/EmptyState";
import { ErrorMessage } from "@/components/app/ErrorMessage";
import { useListRecords } from "@/lib/api/generated/endpoints/records/records";
import type { RecordDto } from "@/lib/api/generated/model/recordDto";
import { useSystemCollection } from "@/lib/hooks/useSystemCollection";
import { tintClass } from "@/lib/collection-tint";
import { cn } from "@/lib/utils";

const WEEKDAYS = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];
const GRID_DAYS = 42;
const MONTH_FORMAT = new Intl.DateTimeFormat("en-GB", { month: "long", year: "numeric" });
const DAY_FORMAT = new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "long", year: "numeric" });
const TIME_FORMAT = new Intl.DateTimeFormat("en-GB", { hour: "2-digit", minute: "2-digit" });
const UPCOMING_FORMAT = new Intl.DateTimeFormat("en-GB", { weekday: "short", day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" });

function startOfMonth(date: Date): Date {
  return new Date(date.getFullYear(), date.getMonth(), 1);
}

function addDays(date: Date, days: number): Date {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate() + days);
}

function dayKey(date: Date): string {
  return `${date.getFullYear()}-${date.getMonth()}-${date.getDate()}`;
}

/** Monday on or before the first of the month. */
function gridStart(month: Date): Date {
  return addDays(month, -((month.getDay() + 6) % 7));
}

function startsAt(record: RecordDto): Date | null {
  const value = record.values.starts_at;
  return typeof value === "string" ? new Date(value) : null;
}

/** The assistant's calendar (system collection `events`): a month grid plus the next upcoming events. */
export function CalendarView() {
  const { collection, isLoading, error } = useSystemCollection("events");
  const [month, setMonth] = useState(() => startOfMonth(new Date()));
  // Fixed per mount so the upcoming query key stays stable.
  const [now] = useState(() => new Date());

  const first = gridStart(month);
  const last = addDays(first, GRID_DAYS);
  const enabled = collection !== undefined;
  const { data: monthEvents } = useListRecords(
    collection?.id ?? 0,
    { filter: [`starts_at:gte:${first.toISOString()}`, `starts_at:lt:${last.toISOString()}`], sort: "starts_at", size: 200 },
    { query: { enabled } },
  );
  const { data: upcoming } = useListRecords(
    collection?.id ?? 0,
    { filter: [`starts_at:gte:${now.toISOString()}`], sort: "starts_at", size: 8 },
    { query: { enabled } },
  );

  if (isLoading) {
    return <p className="text-sm text-muted-foreground">Loading calendar…</p>;
  }
  if (error) {
    return <ErrorMessage message={error.message} />;
  }
  if (!collection) {
    return <EmptyState icon={CalendarDays} title="No calendar yet" description="The calendar collection is created when the backend starts." />;
  }

  const byDay = new Map<string, RecordDto[]>();
  for (const record of monthEvents?.items ?? []) {
    const start = startsAt(record);
    if (!start) continue;
    const key = dayKey(start);
    byDay.set(key, [...(byDay.get(key) ?? []), record]);
  }
  const today = dayKey(new Date());
  const days = Array.from({ length: GRID_DAYS }, (_, index) => addDays(first, index));

  return (
    <div className="grid gap-6 lg:grid-cols-[1fr_18rem]">
      <section className="grid gap-3">
        <div className="flex items-center gap-2">
          <h2 className="mr-auto text-lg font-semibold">{MONTH_FORMAT.format(month)}</h2>
          <AppButton variant="outline" size="icon" aria-label="Previous month" onClick={() => setMonth(new Date(month.getFullYear(), month.getMonth() - 1, 1))}>
            <ChevronLeft aria-hidden="true" />
          </AppButton>
          <AppButton variant="outline" onClick={() => setMonth(startOfMonth(new Date()))}>
            Today
          </AppButton>
          <AppButton variant="outline" size="icon" aria-label="Next month" onClick={() => setMonth(new Date(month.getFullYear(), month.getMonth() + 1, 1))}>
            <ChevronRight aria-hidden="true" />
          </AppButton>
        </div>
        <div className="grid grid-cols-7 text-center text-xs text-muted-foreground" aria-hidden="true">
          {WEEKDAYS.map((weekday) => (
            <span key={weekday}>{weekday}</span>
          ))}
        </div>
        <ul className="grid grid-cols-7 gap-px overflow-hidden rounded-lg border bg-border">
          {days.map((day) => {
            const events = byDay.get(dayKey(day)) ?? [];
            const inMonth = day.getMonth() === month.getMonth();
            return (
              <li
                key={dayKey(day)}
                aria-label={DAY_FORMAT.format(day)}
                className={cn("flex min-h-24 flex-col gap-1 bg-background p-1.5", !inMonth && "bg-muted/40 text-muted-foreground")}
              >
                <span
                  className={cn(
                    "flex size-6 items-center justify-center rounded-full text-xs",
                    dayKey(day) === today && "bg-primary text-primary-foreground",
                  )}
                >
                  {day.getDate()}
                </span>
                {events.map((event) => (
                  <EventChip key={event.id} event={event} />
                ))}
              </li>
            );
          })}
        </ul>
      </section>

      <section aria-labelledby="upcoming-heading" className="grid content-start gap-3">
        <h2 id="upcoming-heading" className="text-sm font-semibold">
          Upcoming
        </h2>
        {upcoming && upcoming.items.length > 0 ? (
          <ul className="grid gap-2">
            {upcoming.items.map((event) => {
              const start = startsAt(event);
              const location = event.values.location;
              return (
                <li key={event.id} className="grid gap-0.5 rounded-lg border px-3 py-2 text-sm">
                  <span className="font-medium">{event.title}</span>
                  {start ? <span className="text-xs text-muted-foreground">{UPCOMING_FORMAT.format(start)}</span> : null}
                  {typeof location === "string" && location ? <span className="text-xs text-muted-foreground">{location}</span> : null}
                </li>
              );
            })}
          </ul>
        ) : (
          <p className="text-sm text-muted-foreground">Nothing coming up.</p>
        )}
      </section>
    </div>
  );
}

function EventChip({ event }: { event: RecordDto }) {
  const start = startsAt(event);
  const allDay = event.values.all_day === true;
  return (
    <span className={cn("truncate rounded px-1.5 py-0.5 text-xs", tintClass(1))} title={event.title}>
      {start && !allDay ? <span className="mr-1 tabular-nums opacity-75">{TIME_FORMAT.format(start)}</span> : null}
      {event.title}
    </span>
  );
}
