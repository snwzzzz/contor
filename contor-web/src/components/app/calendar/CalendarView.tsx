"use client";

import { keepPreviousData, useQueryClient } from "@tanstack/react-query";
import { CalendarDays, ChevronLeft, ChevronRight, MapPin, Plus } from "lucide-react";
import Link from "next/link";
import {
  createContext,
  useContext,
  useEffect,
  useId,
  useRef,
  useState,
  type ComponentProps,
  type FormEvent,
  type PointerEvent,
  type ReactNode,
} from "react";
import { RecordSheet } from "@/components/app/collections/RecordSheet";
import { AppButton } from "@/components/app/AppButton";
import { recordHref } from "@/components/app/collections/CollectionTable";
import { EmptyState } from "@/components/app/EmptyState";
import { ErrorMessage } from "@/components/app/ErrorMessage";
import { QuietEmpty } from "@/components/app/home/HomeSection";
import { DUE_TONE, stateOf, TASK_LIST_PARAMS } from "@/components/app/tasks/task-model";
import { TimeInput } from "@/components/app/TimeInput";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { getListRecordsQueryKey, useCreateRecord, useListRecords, useUpdateRecord } from "@/lib/api/generated/endpoints/records/records";
import type { RecordDto } from "@/lib/api/generated/model/recordDto";
import {
  addDays,
  layoutDay,
  minutesOf,
  monthDays,
  monthLabel,
  sameDay,
  startOfDay,
  startOfMonth,
  startOfWeek,
  weekDays,
  weekLabel,
  type PlacedEvent,
} from "@/lib/calendar-week";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { ROW_HOVER } from "@/lib/card-hover";
import { describeDue, type DueDescription } from "@/lib/due";
import { useSystemCollection } from "@/lib/hooks/useSystemCollection";
import { formatClock, formatTime, useTimeFormat, type TimeFormat } from "@/lib/time-format";
import { cn } from "@/lib/utils";

/** Height of one hour in the week grid (px). */
export const HOUR_HEIGHT = 48;
const HOURS = Array.from({ length: 24 }, (_, hour) => hour);
const DAY_MINUTES = 24 * 60;
/** Events without an end take an hour; very short ones still get room for their title. */
const DEFAULT_MINUTES = 60;
const MIN_EVENT_HEIGHT = 22;
const ALL_DAY_ROW = 24;

const WEEKDAY_LONG = new Intl.DateTimeFormat("en-GB", { weekday: "long" });
const WEEKDAY_SHORT = new Intl.DateTimeFormat("en-GB", { weekday: "short" });
const DAY_MONTH = new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "long" });
const DAY_MONTH_SHORT = new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "short" });
const YEAR = new Intl.DateTimeFormat("en-GB", { year: "numeric" });

/** "Friday, 9 October". */
function dayTitle(day: Date): string {
  return `${WEEKDAY_LONG.format(day)}, ${DAY_MONTH.format(day)}`;
}

/** "Friday, 9 October 2026". */
function dayTitleWithYear(day: Date): string {
  return `${dayTitle(day)} ${YEAR.format(day)}`;
}

function startsAt(record: RecordDto): Date | null {
  const value = record.values.starts_at;
  return typeof value === "string" ? new Date(value) : null;
}

function isAllDay(record: RecordDto): boolean {
  return record.values.all_day === true;
}

/** Start and end of a timed event, the end kept on the start's day (the grid shows one day per column). */
function span(record: RecordDto, start: Date): { start: Date; end: Date } {
  const value = record.values.ends_at;
  const given = typeof value === "string" ? new Date(value) : null;
  const end = given && given > start ? given : new Date(start.getTime() + DEFAULT_MINUTES * 60_000);
  const midnight = addDays(startOfDay(start), 1);
  return { start, end: end > midnight ? midnight : end };
}

/** "10:00 – 11:00" (or "10:00 AM – 11:00 AM"); an end on another day carries its date ("22:00 – 24 Oct 02:00"). */
function timeRange(record: RecordDto, format: TimeFormat): string {
  const start = startsAt(record);
  if (!start || isAllDay(record)) return "All day";
  const value = record.values.ends_at;
  const given = typeof value === "string" ? new Date(value) : null;
  const end = given && given > start ? given : new Date(start.getTime() + DEFAULT_MINUTES * 60_000);
  const endText = sameDay(start, end) ? formatTime(end, format) : `${DAY_MONTH_SHORT.format(end)} ${formatTime(end, format)}`;
  return `${formatTime(start, format)} – ${endText}`;
}

/** The current time, moving on every minute (for the now line). */
function useNow(): Date {
  const [now, setNow] = useState(() => new Date());
  useEffect(() => {
    const timer = setInterval(() => setNow(new Date()), 60_000);
    return () => clearInterval(timer);
  }, []);
  return now;
}

/** Opens an event in the detail sidebar; provided by CalendarView to every event link inside it. */
const OpenEventContext = createContext<(eventId: number) => void>(() => undefined);

/**
 * An event's link to its page. A plain click opens the event in the detail sidebar instead; Ctrl/⌘/Shift-click and the
 * middle button still open the page (new tab or window), and the address stays for "copy link".
 */
function EventLink({ eventId, onClick, ...props }: ComponentProps<typeof Link> & { eventId: number }) {
  const openEvent = useContext(OpenEventContext);
  return (
    <Link
      {...props}
      onClick={(event) => {
        onClick?.(event);
        if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
        event.preventDefault();
        openEvent(eventId);
      }}
    />
  );
}

type View = "week" | "month";
const VIEWS: readonly { value: View; label: string }[] = [
  { value: "week", label: "Week" },
  { value: "month", label: "Month" },
];
/**
 * What is shown lives in the URL, so every week and month can be linked to and survives a reload:
 * `?view=week&date=2026-10-19` (the week's Monday) or `?view=month&date=2026-10-01` (the 1st). Without `date` the
 * period around today is shown; plain `/calendar` is this week.
 */
const VIEW_PARAM = "view";
const DATE_PARAM = "date";
const DEFAULT_VIEW: View = "week";
const parseView = (raw: string | null): View => (raw === "month" ? "month" : DEFAULT_VIEW);
const DATE_PATTERN = /^(\d{4})-(\d{2})-(\d{2})$/;

/** A real calendar day from YYYY-MM-DD (local), or null ("2026-13-45" is not one). */
function parseDay(raw: string | null): Date | null {
  const match = raw ? DATE_PATTERN.exec(raw) : null;
  if (!match) return null;
  const [year, month, day] = [Number(match[1]), Number(match[2]) - 1, Number(match[3])];
  const date = new Date(year, month, day);
  return date.getFullYear() === year && date.getMonth() === month && date.getDate() === day ? date : null;
}

/** The first day of the week or month around `day`: the `date` an address names. */
function periodStart(view: View, day: Date): Date {
  return view === "week" ? startOfWeek(day) : startOfMonth(day);
}

/** The current address showing `view` around `day` (null: around today, no `date`); other parameters stay. */
function calendarHref(pathname: string, params: URLSearchParams, view: View, day: Date | null): string {
  const next = new URLSearchParams(params);
  next.set(VIEW_PARAM, view);
  if (day) next.set(DATE_PARAM, isoDate(periodStart(view, day)));
  else next.delete(DATE_PARAM);
  return `${pathname}?${next.toString()}`;
}

interface OpenDay {
  day: Date;
  /** "14:00" and "17:00" when opened from the hours (a click or a drag); empty from a day header or month cell. */
  start: string;
  end: string;
}

/** Hours `from` up to (not including) `to` of one day, e.g. a drag over the slots. */
interface HourRange {
  day: Date;
  from: number;
  to: number;
}

/** "09:00"; the end of the day is the last minute ("23:59"), so it stays a time of that day. */
function hourText(hour: number): string {
  return hour >= 24 ? "23:59" : `${String(hour).padStart(2, "0")}:00`;
}

/** An hour in the grid's narrow gutter: "14:00", or "2 PM" in the 12-hour format. */
function hourLabel(hour: number, format: TimeFormat): string {
  return format === "24h" ? hourText(hour) : `${hour % 12 === 0 ? 12 : hour % 12} ${hour < 12 ? "AM" : "PM"}`;
}

/** The hours of an opened day ("14:00"–"17:00") back as a range, for the planned block in the week grid. */
function plannedRange(open: OpenDay | null): HourRange | null {
  if (!open || !open.start || !open.end) return null;
  const from = Number(open.start.slice(0, 2));
  const to = open.end === "23:59" ? 24 : Number(open.end.slice(0, 2));
  return { day: open.day, from, to };
}

/**
 * The assistant's calendar (system collection `events`), as a week or a month (both in the URL). The week: hours
 * down, days across, timed events placed by their start and end, all-day events above the hours and a line at the
 * current time. The month: six weeks of day cells with the first events of each day. Beside it the agenda: overdue
 * to-dos, then each day's events and the to-dos due that day. A day (header, hour slot or month cell) opens that day
 * with a quick "add event" form.
 */
export function CalendarView() {
  const events = useSystemCollection("events");
  const tasks = useSystemCollection("tasks");
  const now = useNow();
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const view = parseView(searchParams.get(VIEW_PARAM));
  // A day inside what is shown (from the URL, else today); the week or month around it is displayed.
  const cursor = parseDay(searchParams.get(DATE_PARAM)) ?? startOfDay(now);
  const [open, setOpen] = useState<OpenDay | null>(null);
  // The event shown in the detail sidebar.
  const [shownEvent, setShownEvent] = useState<number | null>(null);

  const weekStart = startOfWeek(cursor);
  const month = startOfMonth(cursor);
  const days = view === "week" ? weekDays(weekStart) : monthDays(month);
  const rangeStart = days[0];
  const rangeEnd = addDays(days[days.length - 1], 1);
  const eventsId = events.collection?.id ?? 0;
  const tasksId = tasks.collection?.id ?? 0;
  const { data: weekEvents } = useListRecords(
    eventsId,
    { filter: [`starts_at:gte:${rangeStart.toISOString()}`, `starts_at:lt:${rangeEnd.toISOString()}`], sort: "starts_at", size: 200 },
    { query: { enabled: events.collection !== undefined, placeholderData: keepPreviousData } },
  );

  // A history entry per view and period: Back and Forward step through what was looked at (week → month → next month …).
  const show = (nextView: View, day: Date | null) => router.push(calendarHref(pathname, searchParams, nextView, day), { scroll: false });
  // Switching views keeps the opened day: the week or month around it is shown, and it stays selected.
  const changeView = (next: View) => show(next, open ? open.day : cursor);
  const step = (direction: 1 | -1) =>
    show(view, view === "week" ? addDays(weekStart, 7 * direction) : new Date(month.getFullYear(), month.getMonth() + direction, 1));
  const unit = view === "week" ? "week" : "month";
  const { data: taskPage } = useListRecords(tasksId, TASK_LIST_PARAMS, { query: { enabled: tasks.collection !== undefined } });

  if (events.isLoading) {
    return <CalendarSkeleton />;
  }
  if (events.error) {
    return <ErrorMessage message={events.error.message} />;
  }
  if (!events.collection) {
    return <EmptyState icon={CalendarDays} title="No calendar yet" description="The calendar collection is created when the backend starts." />;
  }

  const items = weekEvents?.items ?? [];
  const openTasks = (taskPage?.items ?? []).filter((task) => stateOf(task) !== "done");

  return (
    <OpenEventContext value={setShownEvent}>
      {shownEvent !== null ? (
        <RecordSheet
          key={shownEvent}
          collectionId={events.collection.id}
          recordId={shownEvent}
          icon={CalendarDays}
          onOpenChange={(isOpen) => {
            if (!isOpen) setShownEvent(null);
          }}
        />
      ) : null}
      {/* From lg the calendar is exactly the rest of the screen (main is a flex column): one row of a fixed height, the
          grid filling its column and the agenda scrolling on its own. Smaller screens scroll the page as usual. */}
      <div className="grid grid-cols-[minmax(0,1fr)] items-start gap-6 lg:min-h-0 lg:flex-1 lg:grid-cols-[minmax(0,1fr)_20rem] lg:grid-rows-[minmax(0,1fr)] lg:items-stretch">
        {/* minmax(0,1fr): the grid's minimum width scrolls inside its box instead of widening the page. */}
        <section aria-labelledby="week-heading" className="grid min-w-0 grid-cols-[minmax(0,1fr)] gap-3 lg:flex lg:min-h-0 lg:flex-col">
          <div className="flex flex-wrap items-center gap-2">
            <h2 id="week-heading" className="mr-auto text-lg font-semibold tracking-tight">
              {view === "week" ? weekLabel(weekStart) : monthLabel(month)}
            </h2>
            <ViewSwitch value={view} onChange={changeView} />
            <AppButton variant="outline" size="icon" aria-label={`Previous ${unit}`} onClick={() => step(-1)}>
              <ChevronLeft aria-hidden="true" />
            </AppButton>
            {/* No date in the address for today, so a bookmark of it stays "this week" tomorrow. */}
            <AppButton variant="outline" onClick={() => show(view, null)}>
              Today
            </AppButton>
            <AppButton variant="outline" size="icon" aria-label={`Next ${unit}`} onClick={() => step(1)}>
              <ChevronRight aria-hidden="true" />
            </AppButton>
          </div>
          {view === "week" ? (
            <WeekGrid
              key={weekStart.getTime()}
              days={days}
              events={items}
              collectionId={events.collection.id}
              now={now}
              selected={open?.day ?? null}
              planned={plannedRange(open)}
              onOpen={(day, from, to) => setOpen({ day, start: hourText(from), end: hourText(to) })}
              onOpenDay={(day) => setOpen({ day, start: "", end: "" })}
            />
          ) : (
            <MonthGrid
              days={days}
              month={month}
              events={items}
              collectionId={events.collection.id}
              now={now}
              selected={open?.day ?? null}
              onOpen={(day) => setOpen({ day, start: "", end: "" })}
            />
          )}
        </section>

        {open ? (
          <DayAgenda
            key={`${open.day.getTime()}-${open.start}-${open.end}`}
            day={open.day}
            initialStart={open.start}
            initialEnd={open.end}
            collectionId={events.collection.id}
            events={items.filter((event) => {
              const start = startsAt(event);
              return start !== null && sameDay(start, open.day);
            })}
            onClose={() => setOpen(null)}
          />
        ) : (
          <WeekAgenda
            // The month's own days, not the neighbouring ones that fill its grid.
            days={view === "week" ? days : days.filter((day) => day.getMonth() === month.getMonth())}
            events={items}
            eventsId={events.collection.id}
            tasks={openTasks}
            tasksId={tasksId}
            now={now}
          />
        )}
      </div>
    </OpenEventContext>
  );
}

/** Week or month as a segmented radio group (like the theme setting); the choice is stored right away. */
function ViewSwitch({ value, onChange }: { value: View; onChange: (view: View) => void }) {
  const name = useId();
  return (
    <div role="radiogroup" aria-label="Calendar view" className="flex rounded-lg bg-muted p-0.5">
      {VIEWS.map((option) => (
        <label
          key={option.value}
          className={cn(
            "relative flex h-7 cursor-pointer items-center rounded-md px-3 text-sm text-muted-foreground hover:text-foreground",
            "has-[:focus-visible]:ring-2 has-[:focus-visible]:ring-ring",
            value === option.value && "bg-background text-foreground shadow-xs",
          )}
        >
          <input
            type="radio"
            name={name}
            value={option.value}
            checked={value === option.value}
            onChange={() => onChange(option.value)}
            className="sr-only"
          />
          {option.label}
        </label>
      ))}
    </div>
  );
}

interface MonthGridProps {
  days: Date[];
  month: Date;
  events: RecordDto[];
  collectionId: number;
  now: Date;
  /** The day open in the side panel, marked in the grid. */
  selected: Date | null;
  onOpen: (day: Date) => void;
}

/**
 * Six weeks of day cells (the month's own days in front, the neighbouring ones greyed out). Each cell lists all its
 * events (all-day ones first) and scrolls them on its own when they do not fit, with a scrollbar that only shows while
 * the pointer is over the cell; the cell itself opens the day. From lg it fills the page; it never scrolls sideways.
 */
function MonthGrid({ days, month, events, collectionId, now, selected, onOpen }: MonthGridProps) {
  return (
    <div data-testid="month-grid" className="overflow-x-hidden rounded-lg border lg:flex lg:min-h-0 lg:flex-1 lg:flex-col">
      <div className="grid grid-cols-7 border-b" aria-hidden="true">
        {days.slice(0, 7).map((day) => (
          <span key={day.getTime()} className="flex h-9 min-w-0 items-center justify-center text-sm text-muted-foreground">
            {WEEKDAY_SHORT.format(day)}
          </span>
        ))}
      </div>
      <ul className="grid grid-cols-7 lg:min-h-0 lg:flex-1 lg:grid-rows-6">
        {days.map((day, index) => {
          const today = sameDay(day, now);
          const inMonth = day.getMonth() === month.getMonth();
          const dayEvents = events
            .filter((event) => sameDay(startsAt(event) ?? new Date(0), day))
            .sort((a, b) => Number(isAllDay(b)) - Number(isAllDay(a)));
          const isSelected = selected !== null && sameDay(day, selected);
          return (
            <li
              key={day.getTime()}
              aria-label={dayTitleWithYear(day)}
              className={cn(
                "group/cell relative flex h-28 min-w-0 flex-col gap-1 overflow-hidden p-1.5 transition-colors hover:bg-muted/40 motion-reduce:transition-none lg:h-auto lg:min-h-0",
                index % 7 !== 0 && "border-l",
                index >= 7 && "border-t",
                // Weekends shaded lightly; the neighbouring months' days greyed out clearly.
                inMonth ? index % 7 >= 5 && "bg-muted/30" : "bg-muted/60 text-muted-foreground",
                // The open day: the accent background and a ring on the inside (the hairlines stay).
                isSelected && "bg-accent ring-2 ring-ring ring-inset hover:bg-accent",
              )}
            >
              {/* The whole cell opens the day; the date and the event list sit above this button. */}
              <button
                type="button"
                aria-label={`Open ${dayTitle(day)}`}
                aria-pressed={isSelected}
                onClick={() => onOpen(day)}
                className="absolute inset-0 cursor-pointer outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-inset"
              />
              <span
                aria-current={today ? "date" : undefined}
                className={cn(
                  "pointer-events-none relative flex h-6 w-fit min-w-6 shrink-0 items-center justify-center rounded-md px-1 text-sm tabular-nums",
                  today && "bg-primary font-medium text-primary-foreground",
                  !inMonth && "opacity-60",
                )}
              >
                {day.getDate()}
              </span>
              {dayEvents.length > 0 ? (
                // Scrolls when the events do not fit; a click on its empty space still opens the day.
                <div
                  data-testid="cell-events"
                  onClick={(event) => {
                    if (event.target === event.currentTarget) onOpen(day);
                  }}
                  className={cn(
                    "relative -mr-1 grid min-h-0 flex-1 content-start gap-1 overflow-x-hidden overflow-y-auto pr-1 [scrollbar-gutter:stable]",
                    // The thumb only shows while the pointer is over the cell (Chromium/WebKit and Firefox).
                    "[scrollbar-color:transparent_transparent] group-hover/cell:[scrollbar-color:var(--scrollbar)_transparent]",
                    "[&::-webkit-scrollbar-thumb]:bg-transparent group-hover/cell:[&::-webkit-scrollbar-thumb]:bg-(--scrollbar)",
                  )}
                >
                  {dayEvents.map((event) => (
                    <MonthChip key={event.id} event={event} collectionId={collectionId} faded={!inMonth || (startsAt(event) ?? now) < now} />
                  ))}
                </div>
              ) : null}
            </li>
          );
        })}
      </ul>
    </div>
  );
}

/** An event in a month cell: all-day ones as a tinted bar, timed ones as a tinted dot, the time and the title. */
function MonthChip({ event, collectionId, faded }: { event: RecordDto; collectionId: number; faded: boolean }) {
  const format = useTimeFormat();
  const start = startsAt(event);
  const allDay = isAllDay(event);
  return (
    <EventLink eventId={event.id}
      href={recordHref(collectionId, event.id)}
      title={`${event.title} · ${timeRange(event, format)}`}
      className={cn(
        "relative flex min-w-0 items-start gap-1.5 rounded-sm px-1.5 py-0.5 text-xs leading-4 outline-none transition-opacity focus-visible:ring-2 focus-visible:ring-ring",
        allDay ? "border-l-2 border-tint-1-vivid bg-tint-1 text-tint-1-foreground hover:opacity-80" : "text-foreground hover:bg-muted",
        faded && "opacity-60",
      )}
    >
      {allDay ? null : <span aria-hidden="true" className="mt-[5px] size-1.5 shrink-0 rounded-full bg-tint-1-vivid" />}
      {/* Narrow cells keep the room for the title; the time is in the tooltip and the agenda. */}
      {start && !allDay ? <span className="hidden shrink-0 text-muted-foreground tabular-nums xl:inline">{formatTime(start, format)}</span> : null}
      {/* The whole title, wrapped between words (the cell scrolls when it gets long); a word only breaks when it is
          wider than the cell itself. */}
      <span className="min-w-0 break-words hyphens-auto">{event.title || "(Untitled event)"}</span>
    </EventLink>
  );
}

interface WeekGridProps {
  days: Date[];
  events: RecordDto[];
  collectionId: number;
  now: Date;
  /** The day open in the side panel, marked in its header. */
  selected: Date | null;
  /** The hours being planned in the side panel, drawn as a placeholder block. */
  planned: HourRange | null;
  /** Hours `from` up to `to` of `day` were clicked or dragged over. */
  onOpen: (day: Date, from: number, to: number) => void;
  onOpenDay: (day: Date) => void;
}

/**
 * Seven day columns over 24 hour rows; scrolls on its own (both ways on small screens) with a sticky day header.
 * Pressing on an hour and dragging up or down over others plans an event over all of them (one hour on a click).
 */
function WeekGrid({ days, events, collectionId, now, selected, planned, onOpen, onOpenDay }: WeekGridProps) {
  const format = useTimeFormat();
  const scroller = useRef<HTMLDivElement>(null);
  const thisWeek = days.some((day) => sameDay(day, now));
  const firstHour = thisWeek ? Math.min(Math.max(now.getHours() - 3, 0), 15) : 7;
  // The slot pressed first and the one the pointer is over now (same day only). The ref is what the release reads:
  // it is up to date even when the pointer is released before React re-rendered.
  const [drag, setDrag] = useState<{ day: Date; anchor: number; current: number } | null>(null);
  const dragRef = useRef(drag);
  // A press ends in pointerup and then a click on the slot; the drag already opened the range, so that click is skipped.
  const finishedDrag = useRef(false);

  // Start at the working day (or a little before now) instead of midnight; once per week shown.
  useEffect(() => {
    if (scroller.current) scroller.current.scrollTop = firstHour * HOUR_HEIGHT;
    // eslint-disable-next-line react-hooks/exhaustive-deps -- only on mount (the grid is keyed by its week)
  }, []);

  const moveDrag = (next: typeof drag) => {
    dragRef.current = next;
    setDrag(next);
  };

  const slotHandlers = {
    onPointerDown: (day: Date, hour: number, event: PointerEvent<HTMLButtonElement>) => {
      if (event.button > 0) return;
      // No text selection or native drag while sweeping over the hours.
      event.preventDefault();
      finishedDrag.current = false;
      moveDrag({ day, anchor: hour, current: hour });
      // The drag ends wherever the pointer is released, also outside the grid.
      window.addEventListener(
        "pointerup",
        () => {
          const done = dragRef.current;
          moveDrag(null);
          if (!done) return;
          finishedDrag.current = true;
          onOpen(done.day, Math.min(done.anchor, done.current), Math.max(done.anchor, done.current) + 1);
        },
        { once: true },
      );
    },
    onPointerEnter: (day: Date, hour: number) => {
      const current = dragRef.current;
      if (current && sameDay(current.day, day) && current.current !== hour) moveDrag({ ...current, current: hour });
    },
    onClick: (day: Date, hour: number) => {
      if (finishedDrag.current) {
        finishedDrag.current = false;
        return;
      }
      onOpen(day, hour, hour + 1);
    },
  };
  const range: HourRange | null = drag
    ? { day: drag.day, from: Math.min(drag.anchor, drag.current), to: Math.max(drag.anchor, drag.current) + 1 }
    : planned;

  const allDay = days.map((day) => events.filter((event) => isAllDay(event) && sameDay(startsAt(event) ?? new Date(0), day)));
  const allDayRows = Math.max(0, ...allDay.map((list) => list.length));
  const allDayHeight = allDayRows > 0 ? allDayRows * ALL_DAY_ROW + 8 : 0;

  return (
    <div
      ref={scroller}
      data-testid="week-scroller"
      // Only the hours scroll; the seven days always share the width (no sideways scrolling, also on phones).
      className={cn(
        "max-h-[40rem] overflow-x-hidden overflow-y-auto rounded-lg border lg:max-h-none lg:min-h-0 lg:flex-1",
        drag && "cursor-ns-resize select-none",
      )}
    >
      <div className="grid grid-cols-[3rem_repeat(7,minmax(0,1fr))] sm:grid-cols-[3.5rem_repeat(7,minmax(0,1fr))]">
        {/* Hour labels. */}
        <div aria-hidden="true">
          <div className="sticky top-0 z-20 flex flex-col justify-end border-b bg-background" style={{ height: 48 + allDayHeight }}>
            {allDayRows > 0 ? <span className="px-2 pb-2 text-xs text-muted-foreground">All day</span> : null}
          </div>
          <div className="relative" style={{ height: 24 * HOUR_HEIGHT }}>
            {HOURS.slice(1).map((hour) => (
              <span key={hour} className="absolute right-2 -translate-y-1/2 text-xs text-muted-foreground tabular-nums" style={{ top: hour * HOUR_HEIGHT }}>
                {hourLabel(hour, format)}
              </span>
            ))}
          </div>
        </div>

        {days.map((day, index) => (
          <DayColumn
            key={day.getTime()}
            day={day}
            weekend={index >= 5}
            allDay={allDay[index]}
            allDayHeight={allDayHeight}
            timed={events.filter((event) => !isAllDay(event) && sameDay(startsAt(event) ?? new Date(0), day))}
            collectionId={collectionId}
            now={now}
            selected={selected !== null && sameDay(day, selected)}
            range={range && sameDay(range.day, day) ? range : null}
            slots={slotHandlers}
            onOpenDay={onOpenDay}
          />
        ))}
      </div>
    </div>
  );
}

interface SlotHandlers {
  onPointerDown: (day: Date, hour: number, event: PointerEvent<HTMLButtonElement>) => void;
  onPointerEnter: (day: Date, hour: number) => void;
  onClick: (day: Date, hour: number) => void;
}

interface DayColumnProps {
  day: Date;
  weekend: boolean;
  allDay: RecordDto[];
  allDayHeight: number;
  timed: RecordDto[];
  collectionId: number;
  now: Date;
  selected: boolean;
  /** Hours being dragged over or planned on this day. */
  range: HourRange | null;
  slots: SlotHandlers;
  onOpenDay: (day: Date) => void;
}

function DayColumn({ day, weekend, allDay, allDayHeight, timed, collectionId, now, selected, range, slots, onOpenDay }: DayColumnProps) {
  const format = useTimeFormat();
  const today = sameDay(day, now);
  const placed = layoutDay(
    timed.flatMap((event) => {
      const start = startsAt(event);
      return start ? [{ id: event.id, ...span(event, start) }] : [];
    }),
  );
  const byId = new Map(timed.map((event) => [event.id, event]));

  return (
    <div role="group" aria-label={dayTitleWithYear(day)} className={cn("min-w-0 border-l", weekend && "bg-muted/30")}>
      <div className="sticky top-0 z-10 border-b bg-background">
        <button
          type="button"
          aria-label={`Open ${dayTitle(day)}`}
          aria-pressed={selected}
          onClick={() => onOpenDay(day)}
          className={cn(
            // Narrow columns (phones) stack the weekday over the date; from sm they sit side by side.
            "flex h-12 w-full flex-col items-center justify-center text-sm outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-inset sm:flex-row sm:gap-1.5",
            // The open day: accent background with a 2px line along the bottom of its header.
            selected ? "bg-accent shadow-[inset_0_-2px_0_var(--ring)]" : ROW_HOVER,
          )}
        >
          <span className="text-xs text-muted-foreground sm:text-sm">{WEEKDAY_SHORT.format(day)}</span>
          <span
            aria-current={today ? "date" : undefined}
            className={cn("flex h-6 min-w-6 items-center justify-center rounded-md px-1 tabular-nums", today ? "bg-primary font-medium text-primary-foreground" : "font-medium")}
          >
            {day.getDate()}
          </span>
        </button>
        {allDayHeight > 0 ? (
          <ul data-all-day="" className="grid content-start gap-1 px-1 pb-2" style={{ height: allDayHeight }}>
            {allDay.map((event) => (
              <li key={event.id}>
                <EventLink eventId={event.id}
                  href={recordHref(collectionId, event.id)}
                  title={event.title}
                  className="block truncate rounded-sm border-l-2 border-tint-1-vivid bg-tint-1 px-1.5 py-0.5 text-xs text-tint-1-foreground transition-opacity hover:opacity-80"
                >
                  {event.title || "(Untitled event)"}
                </EventLink>
              </li>
            ))}
          </ul>
        ) : null}
      </div>

      <div className="relative" style={{ height: 24 * HOUR_HEIGHT }}>
        {/* The hour rows are the lines of the grid; a click plans that hour, a drag the hours it sweeps over. */}
        {HOURS.map((hour) => (
          <button
            key={hour}
            type="button"
            tabIndex={-1}
            aria-label={`New event on ${dayTitle(day)} at ${formatClock(hourText(hour), format)}`}
            onPointerDown={(event) => slots.onPointerDown(day, hour, event)}
            onPointerEnter={() => slots.onPointerEnter(day, hour)}
            onClick={() => slots.onClick(day, hour)}
            className="block w-full touch-none border-b border-border/60 transition-colors last:border-b-0 hover:bg-muted/40 motion-reduce:transition-none"
            style={{ height: HOUR_HEIGHT }}
          />
        ))}

        {placed.map((entry) => {
          const event = byId.get(entry.id);
          return event ? <EventBlock key={entry.id} event={event} placed={entry} collectionId={collectionId} past={entry.end <= now} /> : null;
        })}

        {range ? (
          // The event being planned: a dashed outline over the hours, above the existing events.
          <div
            data-testid="planned-event"
            aria-hidden="true"
            className="pointer-events-none absolute inset-x-0.5 z-[6] rounded-sm border border-dashed border-foreground/60 bg-background/80 px-1.5 py-1 text-xs"
            style={{ top: range.from * HOUR_HEIGHT, height: (range.to - range.from) * HOUR_HEIGHT }}
          >
            <span className="block font-medium">New event</span>
            <span className="block text-muted-foreground tabular-nums">
              {formatClock(hourText(range.from), format)} – {formatClock(hourText(range.to), format)}
            </span>
          </div>
        ) : null}

        {today ? (
          <div
            data-testid="now-line"
            aria-hidden="true"
            className="pointer-events-none absolute inset-x-0 z-10 h-0.5 bg-primary"
            style={{ top: (minutesOf(now) / 60) * HOUR_HEIGHT }}
          >
            <span className="absolute -top-[3px] -left-1 size-2 rounded-full bg-primary" />
          </div>
        ) : null}
      </div>
    </div>
  );
}

/** A timed event in its day column: placed by start and end, side by side with the events it overlaps. */
function EventBlock({ event, placed, collectionId, past }: { event: RecordDto; placed: PlacedEvent; collectionId: number; past: boolean }) {
  const format = useTimeFormat();
  const top = (minutesOf(placed.start) / 60) * HOUR_HEIGHT;
  const endMinutes = sameDay(placed.start, placed.end) ? minutesOf(placed.end) : DAY_MINUTES;
  const height = Math.max(((endMinutes - minutesOf(placed.start)) / 60) * HOUR_HEIGHT, MIN_EVENT_HEIGHT);
  const short = height < 40;
  // 16px lines inside 4px padding: the time takes one, the title gets the rest (at least one), so nothing is cut.
  const titleLines = Math.max(1, Math.floor((height - 8) / 16) - 1);
  return (
    <EventLink eventId={event.id}
      href={recordHref(collectionId, event.id)}
      title={`${event.title} · ${timeRange(event, format)}`}
      className={cn(
        "absolute z-[5] overflow-hidden rounded-sm border-l-2 border-tint-1-vivid bg-tint-1 px-1.5 text-xs text-tint-1-foreground outline-none ring-background transition-opacity hover:opacity-80 focus-visible:ring-2 focus-visible:ring-ring",
        short ? "flex items-center gap-1.5 py-0" : "py-1",
        past && "opacity-60",
      )}
      style={{
        top,
        height,
        left: `calc(${(placed.lane / placed.lanes) * 100}% + 2px)`,
        width: `calc(${100 / placed.lanes}% - 4px)`,
      }}
    >
      <span
        className={cn("leading-4 font-medium", short ? "truncate" : "line-clamp-1 break-words")}
        style={short ? undefined : { WebkitLineClamp: titleLines }}
      >
        {event.title || "(Untitled event)"}
      </span>
      <span className={cn("leading-4 tabular-nums opacity-75", short ? "shrink-0" : "block truncate")}>{timeRange(event, format)}</span>
    </EventLink>
  );
}

interface WeekAgendaProps {
  days: Date[];
  events: RecordDto[];
  eventsId: number;
  tasks: RecordDto[];
  tasksId: number;
  now: Date;
}

interface DueTask {
  task: RecordDto;
  due: DueDescription;
}

/** Overdue to-dos, then each day of the week that has events or to-dos due; quiet rows under small day headings. */
function WeekAgenda({ days, events, eventsId, tasks, tasksId, now }: WeekAgendaProps) {
  const format = useTimeFormat();
  const queryClient = useQueryClient();
  const update = useUpdateRecord({
    mutation: { onSuccess: () => queryClient.invalidateQueries({ queryKey: getListRecordsQueryKey(tasksId) }) },
  });
  const withDue: DueTask[] = tasks.flatMap((task) => {
    const due = describeDue(typeof task.values.due_date === "string" ? task.values.due_date : null, now);
    return due ? [{ task, due }] : [];
  });
  const overdue = withDue.filter((entry) => entry.due.tone === "overdue");
  const groups = days.flatMap((day) => {
    const dayEvents = events.filter((event) => sameDay(startsAt(event) ?? new Date(0), day));
    const dayTasks = withDue.filter((entry) => entry.due.tone !== "overdue" && entry.task.values.due_date === isoDate(day));
    return dayEvents.length + dayTasks.length > 0 ? [{ day, events: dayEvents, tasks: dayTasks }] : [];
  });
  const complete = (task: RecordDto) => update.mutate({ collectionId: tasksId, recordId: task.id, data: { values: { state: "done" } } });

  return (
    // Scrolls on its own beside the calendar (lg), so a long list never stretches the page.
    <section aria-labelledby="agenda-heading" className="grid content-start gap-3 lg:min-h-0 lg:overflow-y-auto lg:pr-1">
      {/* The heading and status stay at the top while the list scrolls under them. */}
      <div className="grid gap-3 bg-background pb-1 lg:sticky lg:top-0 lg:z-10">
        <h2 id="agenda-heading" className="flex h-8 items-center border-b text-sm font-medium">
          Agenda
        </h2>
        <p className="flex items-center gap-2 text-xs text-muted-foreground">
          <span aria-hidden="true" className={cn("size-1.5 rounded-full", overdue.length > 0 ? "bg-destructive" : "bg-success")} />
          {overdue.length === 0 ? "All to-dos are on time" : overdue.length === 1 ? "1 to-do overdue" : `${overdue.length} to-dos overdue`}
        </p>
      </div>

      {overdue.length > 0 ? (
        <AgendaGroup label="Overdue" title="Overdue">
          {overdue.map((entry) => (
            <TaskRow key={entry.task.id} entry={entry} tasksId={tasksId} busy={update.isPending && update.variables?.recordId === entry.task.id} onDone={complete} />
          ))}
        </AgendaGroup>
      ) : null}

      {groups.map((group) => (
        <AgendaGroup
          key={group.day.getTime()}
          label={dayTitle(group.day)}
          title={sameDay(group.day, now) ? "Today" : DAY_MONTH_SHORT.format(group.day)}
          aside={WEEKDAY_SHORT.format(group.day)}
          current={sameDay(group.day, now)}
        >
          {group.events.map((event) => (
            <li key={`event-${event.id}`}>
              <EventLink eventId={event.id} href={recordHref(eventsId, event.id)} className={cn("flex items-start gap-3 rounded-md px-2 py-1.5 outline-none focus-visible:ring-2 focus-visible:ring-ring", ROW_HOVER)}>
                <CalendarDays aria-hidden="true" className="mt-0.5 size-4 shrink-0 text-muted-foreground" strokeWidth={1.75} />
                <span className="grid min-w-0 flex-1 grid-cols-[minmax(0,1fr)] gap-0.5">
                  <span className="text-sm break-words">{event.title || "(Untitled event)"}</span>
                  <span className="text-xs text-muted-foreground tabular-nums">{timeRange(event, format)}</span>
                  {typeof event.values.location === "string" && event.values.location ? (
                    <span className="flex items-start gap-1 text-xs break-words text-muted-foreground">
                      <MapPin aria-hidden="true" className="mt-0.5 size-3 shrink-0" />
                      {event.values.location}
                    </span>
                  ) : null}
                </span>
              </EventLink>
            </li>
          ))}
          {group.tasks.map((entry) => (
            <TaskRow key={`task-${entry.task.id}`} entry={entry} tasksId={tasksId} busy={update.isPending && update.variables?.recordId === entry.task.id} onDone={complete} />
          ))}
        </AgendaGroup>
      ))}

      {overdue.length === 0 && groups.length === 0 ? (
        <QuietEmpty icon={CalendarDays} title="Nothing this week" description="No events and no to-dos due. Click a day or an hour to plan something." />
      ) : null}
    </section>
  );
}

/** YYYY-MM-DD of a local day (the to-dos' `due_date`). */
function isoDate(day: Date): string {
  return `${day.getFullYear()}-${String(day.getMonth() + 1).padStart(2, "0")}-${String(day.getDate()).padStart(2, "0")}`;
}

function AgendaGroup({ label, title, aside, current, children }: { label: string; title: string; aside?: string; current?: boolean; children: ReactNode }) {
  return (
    <div role="group" aria-label={label} className="grid gap-1">
      <div className={cn("flex h-7 items-center justify-between rounded-md bg-muted/50 px-2 text-xs", current ? "font-medium text-foreground" : "text-muted-foreground")}>
        <span>{title}</span>
        {aside ? <span>{aside}</span> : null}
      </div>
      <ul className="grid">{children}</ul>
    </div>
  );
}

function TaskRow({ entry, tasksId, busy, onDone }: { entry: DueTask; tasksId: number; busy: boolean; onDone: (task: RecordDto) => void }) {
  const { task, due } = entry;
  return (
    <li className={cn("flex items-start gap-3 rounded-md px-2 py-1.5", ROW_HOVER)}>
      <button
        type="button"
        aria-label={`Mark ${task.title} done`}
        disabled={busy}
        onClick={() => onDone(task)}
        className="mt-0.5 size-4 shrink-0 rounded-full border border-muted-foreground/60 transition-colors hover:border-foreground hover:bg-muted focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none disabled:opacity-50"
      />
      <span className="grid min-w-0 flex-1 grid-cols-[minmax(0,1fr)] gap-0.5">
        <Link href={recordHref(tasksId, task.id)} className="text-sm break-words underline-offset-4 hover:underline">
          {task.title || "(Untitled to-do)"}
        </Link>
        <span className={cn("text-xs", DUE_TONE[due.tone])}>{due.label}</span>
      </span>
    </li>
  );
}

interface DayAgendaProps {
  day: Date;
  /** "14:00" and "17:00" from the hours clicked or dragged over; empty otherwise. */
  initialStart: string;
  initialEnd: string;
  collectionId: number;
  events: RecordDto[];
  onClose: () => void;
}

/** "14:30" on `day` as a date, or null for an empty or invalid time. */
function atTime(day: Date, time: string): Date | null {
  const [hours, minutes] = time.split(":").map(Number);
  if (!Number.isInteger(hours) || !Number.isInteger(minutes)) return null;
  return new Date(day.getFullYear(), day.getMonth(), day.getDate(), hours, minutes);
}

/** One day: its events and a quick form to add an event (from–until, or all day). */
function DayAgenda({ day, initialStart, initialEnd, collectionId, events, onClose }: DayAgendaProps) {
  const format = useTimeFormat();
  const queryClient = useQueryClient();
  const headingId = useId();
  const [title, setTitle] = useState("");
  const [time, setTime] = useState(initialStart);
  const [until, setUntil] = useState(initialEnd);
  const [allDay, setAllDay] = useState(false);
  const create = useCreateRecord({
    mutation: {
      onSuccess: () => {
        setTitle("");
        setTime("");
        setUntil("");
        setAllDay(false);
        return queryClient.invalidateQueries({ queryKey: getListRecordsQueryKey(collectionId) });
      },
    },
  });

  function add(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const name = title.trim();
    if (!name) return;
    // No time means the event takes the whole day; an end only counts after the start.
    const start = allDay ? null : atTime(day, time);
    const end = start ? atTime(day, until) : null;
    const values = start
      ? { starts_at: start.toISOString(), ...(end && end > start ? { ends_at: end.toISOString() } : {}), all_day: false }
      : { starts_at: new Date(day.getFullYear(), day.getMonth(), day.getDate()).toISOString(), all_day: true };
    create.mutate({ collectionId, data: { title: name, values } });
  }

  return (
    <section
      aria-labelledby={headingId}
      className="grid content-start gap-4 animate-in fade-in slide-in-from-right-2 duration-200 motion-reduce:animate-none lg:min-h-0 lg:overflow-y-auto lg:pr-1"
    >
      <div className="flex h-8 items-center justify-between gap-2 border-b">
        <h2 id={headingId} className="text-sm font-medium">
          {dayTitle(day)}
        </h2>
        <AppButton variant="ghost" size="sm" className="h-7" aria-label="Back to agenda" onClick={onClose}>
          <ChevronLeft aria-hidden="true" data-icon="inline-start" />
          Agenda
        </AppButton>
      </div>

      {events.length > 0 ? (
        <ul className="-mx-2 grid">
          {events.map((event) => (
            <li key={event.id}>
              <EventLink eventId={event.id} href={recordHref(collectionId, event.id)} className={cn("flex items-start gap-3 rounded-md px-2 py-1.5 outline-none focus-visible:ring-2 focus-visible:ring-ring", ROW_HOVER)}>
                <CalendarDays aria-hidden="true" className="mt-0.5 size-4 shrink-0 text-muted-foreground" strokeWidth={1.75} />
                <span className="grid min-w-0 flex-1 grid-cols-[minmax(0,1fr)] gap-0.5">
                  <span className="text-sm break-words">{event.title || "(Untitled event)"}</span>
                  <span className="text-xs text-muted-foreground tabular-nums">{timeRange(event, format)}</span>
                </span>
              </EventLink>
            </li>
          ))}
        </ul>
      ) : (
        <p className="text-sm text-muted-foreground">Nothing planned.</p>
      )}

      <form onSubmit={add} className="grid gap-2 border-t pt-4">
        <Input aria-label="Event title" placeholder="New event…" value={title} onChange={(event) => setTitle(event.target.value)} autoFocus={initialStart !== ""} />
        <div className="flex flex-wrap items-center gap-x-3 gap-y-2 text-sm">
          <label className="flex items-center gap-2">
            <span className="text-muted-foreground">Time</span>
            <TimeInput value={time} disabled={allDay} onChange={setTime} />
          </label>
          <label className="flex items-center gap-2">
            <span className="text-muted-foreground">Until</span>
            <TimeInput value={until} disabled={allDay} onChange={setUntil} />
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

/** The week header and the hour grid as placeholders, so the page keeps its shape while the calendar loads. */
function CalendarSkeleton() {
  return (
    <div
      role="status"
      aria-label="Loading calendar"
      aria-busy="true"
      className="grid grid-cols-[minmax(0,1fr)] gap-6 lg:min-h-0 lg:flex-1 lg:grid-cols-[minmax(0,1fr)_20rem] lg:grid-rows-[minmax(0,1fr)]"
    >
      <div className="grid gap-3 lg:flex lg:min-h-0 lg:flex-col">
        <div className="flex items-center gap-2">
          <Skeleton className="h-7 w-48" />
          <Skeleton className="ml-auto size-8" />
          <Skeleton className="h-8 w-16" />
          <Skeleton className="size-8" />
        </div>
        <div className="grid h-[40rem] grid-cols-[3.5rem_repeat(7,minmax(0,1fr))] overflow-hidden rounded-lg border lg:h-auto lg:min-h-0 lg:flex-1">
          <div />
          {Array.from({ length: 7 }, (_, index) => (
            <div key={index} className="grid content-start gap-3 border-l p-2">
              <Skeleton className="mx-auto h-5 w-12" />
              {index % 3 === 1 ? <Skeleton className="mt-16 h-20 w-full" /> : null}
              {index % 4 === 2 ? <Skeleton className="mt-32 h-10 w-full" /> : null}
            </div>
          ))}
        </div>
      </div>
      <div className="grid content-start gap-3">
        <Skeleton className="h-8 w-full" />
        {Array.from({ length: 4 }, (_, index) => (
          <div key={index} className="grid gap-2">
            <Skeleton className="h-7 w-full" />
            <Skeleton className={cn("h-4", index % 2 === 0 ? "w-3/4" : "w-1/2")} />
          </div>
        ))}
      </div>
    </div>
  );
}
