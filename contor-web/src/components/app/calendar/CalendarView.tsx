"use client";

import { useQueryClient } from "@tanstack/react-query";
import { CalendarDays, ChevronLeft, ChevronRight, MapPin, Plus, X } from "lucide-react";
import Link from "next/link";
import { useId, useState, type FormEvent } from "react";
import { AppButton } from "@/components/app/AppButton";
import { recordHref } from "@/components/app/collections/CollectionTable";
import { EmptyState } from "@/components/app/EmptyState";
import { ErrorMessage } from "@/components/app/ErrorMessage";
import { Input } from "@/components/ui/input";
import { getListRecordsQueryKey, useCreateRecord, useListRecords } from "@/lib/api/generated/endpoints/records/records";
import type { RecordDto } from "@/lib/api/generated/model/recordDto";
import { useSystemCollection } from "@/lib/hooks/useSystemCollection";
import { CALENDAR_TINT, tintClass } from "@/lib/collection-tint";
import { cn } from "@/lib/utils";

const WEEKDAYS = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];
const GRID_DAYS = 42;
const MONTH_FORMAT = new Intl.DateTimeFormat("en-GB", { month: "long", year: "numeric" });
const DAY_FORMAT = new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "long", year: "numeric" });
const TIME_FORMAT = new Intl.DateTimeFormat("en-GB", { hour: "2-digit", minute: "2-digit" });
const UPCOMING_FORMAT = new Intl.DateTimeFormat("en-GB", { weekday: "short", day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" });
const WEEKDAY_FORMAT = new Intl.DateTimeFormat("en-GB", { weekday: "long" });
const DAY_MONTH_FORMAT = new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "long" });

/** "Friday, 9 October". */
function agendaTitle(day: Date): string {
  return `${WEEKDAY_FORMAT.format(day)}, ${DAY_MONTH_FORMAT.format(day)}`;
}

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

/**
 * The assistant's calendar (system collection `events`): a month grid with clickable days and events, the selected
 * day's agenda with a quick "add event" form, or the next upcoming events when no day is selected.
 */
export function CalendarView() {
  const { collection, isLoading, error } = useSystemCollection("events");
  const [month, setMonth] = useState(() => startOfMonth(new Date()));
  const [selected, setSelected] = useState<Date | null>(null);
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
  const selectedKey = selected ? dayKey(selected) : null;
  const days = Array.from({ length: GRID_DAYS }, (_, index) => addDays(first, index));

  function goToToday() {
    setMonth(startOfMonth(new Date()));
    setSelected(null);
  }

  return (
    <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_18rem]">
      <section className="grid min-w-0 content-start gap-3">
        <div className="flex items-center gap-2">
          <h2 className="mr-auto text-lg font-semibold">{MONTH_FORMAT.format(month)}</h2>
          <AppButton variant="outline" size="icon" aria-label="Previous month" onClick={() => setMonth(new Date(month.getFullYear(), month.getMonth() - 1, 1))}>
            <ChevronLeft aria-hidden="true" />
          </AppButton>
          <AppButton variant="outline" onClick={goToToday}>
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
            const key = dayKey(day);
            const events = byDay.get(key) ?? [];
            const inMonth = day.getMonth() === month.getMonth();
            const isSelected = key === selectedKey;
            return (
              <li
                key={key}
                aria-label={DAY_FORMAT.format(day)}
                className={cn(
                  "relative flex min-h-24 flex-col gap-1 bg-background p-1.5 transition-colors hover:bg-muted/50",
                  !inMonth && "bg-muted/40 text-muted-foreground",
                  isSelected && "bg-primary/5 ring-2 ring-primary ring-inset",
                )}
              >
                {/* The whole cell selects the day; event links sit above this button. */}
                <button
                  type="button"
                  aria-label={`Select ${DAY_FORMAT.format(day)}`}
                  aria-pressed={isSelected}
                  onClick={() => setSelected(day)}
                  className="absolute inset-0 cursor-pointer rounded-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none focus-visible:ring-inset"
                />
                <span
                  className={cn(
                    "pointer-events-none relative flex size-6 items-center justify-center rounded-full text-xs",
                    key === today && "bg-primary text-primary-foreground",
                  )}
                >
                  {day.getDate()}
                </span>
                {events.map((event) => (
                  <EventChip key={event.id} event={event} collectionId={collection.id} />
                ))}
              </li>
            );
          })}
        </ul>
      </section>

      {selected ? (
        <DayAgenda
          key={selectedKey}
          day={selected}
          collectionId={collection.id}
          events={byDay.get(dayKey(selected)) ?? []}
          onClose={() => setSelected(null)}
        />
      ) : (
        <section aria-labelledby="upcoming-heading" className="grid content-start gap-3">
          <h2 id="upcoming-heading" className="text-sm font-semibold">
            Upcoming
          </h2>
          {upcoming && upcoming.items.length > 0 ? (
            <ul className="grid gap-2">
              {upcoming.items.map((event) => (
                <li key={event.id}>
                  <EventCard event={event} collectionId={collection.id} format={UPCOMING_FORMAT} />
                </li>
              ))}
            </ul>
          ) : (
            <p className="text-sm text-muted-foreground">Nothing coming up. Click a day to plan something.</p>
          )}
        </section>
      )}
    </div>
  );
}

interface DayAgendaProps {
  day: Date;
  collectionId: number;
  events: RecordDto[];
  onClose: () => void;
}

/** One day: its events and a quick form to add an event (time or all day). */
function DayAgenda({ day, collectionId, events, onClose }: DayAgendaProps) {
  const queryClient = useQueryClient();
  const headingId = useId();
  const [title, setTitle] = useState("");
  const [time, setTime] = useState("");
  const [allDay, setAllDay] = useState(false);
  const create = useCreateRecord({
    mutation: {
      onSuccess: () => {
        setTitle("");
        setTime("");
        setAllDay(false);
        return queryClient.invalidateQueries({ queryKey: getListRecordsQueryKey(collectionId) });
      },
    },
  });

  function add(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const name = title.trim();
    if (!name) return;
    // No time means the event takes the whole day.
    const [hours, minutes] = time.split(":").map(Number);
    const timed = !allDay && Number.isInteger(hours) && Number.isInteger(minutes);
    const start = timed
      ? new Date(day.getFullYear(), day.getMonth(), day.getDate(), hours, minutes)
      : new Date(day.getFullYear(), day.getMonth(), day.getDate());
    create.mutate({ collectionId, data: { title: name, values: { starts_at: start.toISOString(), all_day: !timed } } });
  }

  return (
    <section aria-labelledby={headingId} className="grid content-start gap-4 animate-in fade-in slide-in-from-right-2 duration-200 motion-reduce:animate-none">
      <div className="flex items-center justify-between gap-2">
        <h2 id={headingId} className="text-sm font-semibold">
          {agendaTitle(day)}
        </h2>
        <AppButton variant="ghost" size="icon" className="size-7" aria-label="Back to upcoming" onClick={onClose}>
          <X aria-hidden="true" />
        </AppButton>
      </div>

      {events.length > 0 ? (
        <ul className="grid gap-2">
          {events.map((event) => (
            <li key={event.id}>
              <EventCard event={event} collectionId={collectionId} format={TIME_FORMAT} />
            </li>
          ))}
        </ul>
      ) : (
        <p className="text-sm text-muted-foreground">Nothing planned.</p>
      )}

      <form onSubmit={add} className="grid gap-2 border-t pt-4">
        <Input aria-label="Event title" placeholder="New event…" value={title} onChange={(event) => setTitle(event.target.value)} />
        <div className="flex items-center gap-3 text-sm">
          <label className="flex items-center gap-2">
            <span className="text-muted-foreground">Time</span>
            <Input type="time" value={time} disabled={allDay} onChange={(event) => setTime(event.target.value)} className="w-auto px-2" />
          </label>
          <label className="flex items-center gap-2">
            <input type="checkbox" checked={allDay} onChange={(event) => setAllDay(event.target.checked)} className="size-4 accent-primary" />
            All day
          </label>
        </div>
        <AppButton type="submit" size="sm" disabled={!title.trim()} loading={create.isPending} className="w-fit">
          <Plus aria-hidden="true" data-icon="inline-start" />
          Add event
        </AppButton>
      </form>
    </section>
  );
}

/** An event as a link to its record page: title, time and place. */
function EventCard({ event, collectionId, format }: { event: RecordDto; collectionId: number; format: Intl.DateTimeFormat }) {
  const start = startsAt(event);
  const location = event.values.location;
  const allDay = event.values.all_day === true;
  return (
    <Link href={recordHref(collectionId, event.id)} className="grid gap-0.5 rounded-md border-l-2 border-tint-1-vivid px-3 py-1.5 text-sm transition-colors hover:bg-muted/50">
      <span className="font-medium">{event.title}</span>
      {start ? <span className="text-xs text-muted-foreground">{allDay ? "All day" : format.format(start)}</span> : null}
      {typeof location === "string" && location ? (
        <span className="flex items-center gap-1 text-xs text-muted-foreground">
          <MapPin className="size-3" aria-hidden="true" />
          {location}
        </span>
      ) : null}
    </Link>
  );
}

function EventChip({ event, collectionId }: { event: RecordDto; collectionId: number }) {
  const start = startsAt(event);
  const allDay = event.values.all_day === true;
  return (
    <Link
      href={recordHref(collectionId, event.id)}
      title={event.title}
      className={cn("relative z-10 min-w-0 truncate rounded px-1.5 py-0.5 text-xs transition-opacity hover:opacity-80", tintClass(CALENDAR_TINT))}
    >
      {start && !allDay ? <span className="mr-1 tabular-nums opacity-75">{TIME_FORMAT.format(start)}</span> : null}
      {event.title}
    </Link>
  );
}
