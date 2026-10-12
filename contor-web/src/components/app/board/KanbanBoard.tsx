"use client";

import type { LucideIcon } from "lucide-react";
import { useState, type DragEvent, type ReactNode } from "react";
import { cn } from "@/lib/utils";

export interface BoardColumn<K extends string> {
  key: K;
  title: string;
  icon: LucideIcon;
  /** Color class of the header icon. */
  iconClass?: string;
  /** Text of the column when it has no cards. */
  empty: string;
}

interface KanbanBoardProps<T, K extends string> {
  columns: readonly BoardColumn<K>[];
  /** The cards of each column, in display order. */
  items: Record<K, readonly T[]>;
  getId: (item: T) => number;
  /** The card itself (its own border and background); the board adds dragging. */
  renderCard: (item: T) => ReactNode;
  /** The card as it will look in `column`, shown in the slot where it would land. */
  renderPreview: (item: T, column: K) => ReactNode;
  /** A card was dropped into `column` at `index` (counted without the card itself). Not called for a drop in place. */
  onMove: (item: T, column: K, index: number) => void;
}

interface Drag<K> {
  id: number;
  from: K;
}

interface Over<K> {
  column: K;
  index: number;
}

/** A tilted copy of the card as the drag image, so the card looks picked up. */
function setLiftedDragImage(event: DragEvent<HTMLElement>) {
  const card = event.currentTarget;
  if (typeof event.dataTransfer.setDragImage !== "function") return;
  const ghost = card.cloneNode(true) as HTMLElement;
  ghost.style.position = "fixed";
  ghost.style.top = "-1000px";
  ghost.style.left = "-1000px";
  ghost.style.width = `${card.offsetWidth}px`;
  ghost.style.transform = "rotate(2deg)";
  ghost.classList.add("shadow-lg");
  document.body.appendChild(ghost);
  const box = card.getBoundingClientRect();
  event.dataTransfer.setDragImage(ghost, event.clientX - box.left, event.clientY - box.top);
  // The browser has taken its snapshot once this task runs.
  setTimeout(() => ghost.remove(), 0);
}

/**
 * Columns of cards that can be dragged within a column and between columns. Each column has a fixed height and
 * scrolls on its own. While dragging, a slot with the card's preview opens where it would land (at the pointer, between
 * the cards), the carried card fades out of its place, and the target column lights up.
 */
export function KanbanBoard<T, K extends string>({ columns, items, getId, renderCard, renderPreview, onMove }: KanbanBoardProps<T, K>) {
  const [drag, setDrag] = useState<Drag<K> | null>(null);
  const [over, setOver] = useState<Over<K> | null>(null);
  const dragged = drag ? items[drag.from].find((item) => getId(item) === drag.id) : undefined;

  /** Insertion index at the pointer: the number of other cards whose middle lies above it. */
  function indexAt(column: HTMLElement, clientY: number): number {
    let index = 0;
    for (const row of column.querySelectorAll<HTMLElement>("[data-board-item]")) {
      if (Number(row.dataset.boardItem) === drag?.id) continue;
      const box = row.getBoundingClientRect();
      if (box.top + box.height / 2 < clientY) index++;
    }
    return index;
  }

  function endDrag() {
    setDrag(null);
    setOver(null);
  }

  function drop(event: DragEvent<HTMLElement>, column: K) {
    event.preventDefault();
    if (!drag || !dragged) {
      endDrag();
      return;
    }
    const index = indexAt(event.currentTarget, event.clientY);
    const from = items[drag.from].findIndex((item) => getId(item) === drag.id);
    endDrag();
    if (column !== drag.from || index !== from) onMove(dragged, column, index);
  }

  return (
    <div className="grid gap-4 lg:grid-cols-3">
      {columns.map((column) => {
        const list = items[column.key];
        const hovered = drag !== null && over?.column === column.key;
        // Only another column lights up; the own column just shows where the card would move to.
        const target = hovered && drag.from !== column.key;
        const preview = hovered && dragged ? (
          <li key="drop-preview" data-testid="drop-preview" aria-hidden="true" className="grid animate-drop-slot">
            <div className="min-h-0 overflow-hidden">
              <div className="rounded-lg border border-dashed border-primary bg-card opacity-80 shadow-sm ring-2 ring-primary/15">
                {renderPreview(dragged, column.key)}
              </div>
            </div>
          </li>
        ) : null;
        const rows: ReactNode[] = [];
        let others = 0;
        for (const item of list) {
          const id = getId(item);
          const source = id === drag?.id;
          if (!source && preview && others === over?.index) rows.push(preview);
          if (!source) others++;
          rows.push(
            <li
              key={id}
              data-board-item={id}
              draggable
              hidden={source && hovered}
              data-dragging={source ? "true" : undefined}
              onDragStart={(event) => {
                event.dataTransfer.effectAllowed = "move";
                // Firefox only starts a drag that carries data.
                event.dataTransfer.setData("text/plain", String(id));
                setLiftedDragImage(event);
                setDrag({ id, from: column.key });
              }}
              onDragEnd={endDrag}
              className={cn(
                "cursor-grab transition-[opacity,transform] duration-150 active:cursor-grabbing",
                // What stays behind while the card is carried: a faded, slightly shrunk card.
                source && "scale-[0.98] opacity-40",
              )}
            >
              {renderCard(item)}
            </li>,
          );
        }
        if (preview && others <= (over?.index ?? 0)) rows.push(preview);

        return (
          <section
            key={column.key}
            aria-label={column.title}
            data-drop-target={target ? "true" : undefined}
            onDragOver={(event) => {
              if (drag === null) return;
              event.preventDefault();
              event.dataTransfer.dropEffect = "move";
              const index = indexAt(event.currentTarget, event.clientY);
              if (over?.column !== column.key || over.index !== index) setOver({ column: column.key, index });
            }}
            onDragLeave={(event) => {
              // Leaving for a card inside the column is not leaving the column.
              if (!event.currentTarget.contains(event.relatedTarget as Node | null)) setOver(null);
            }}
            onDrop={(event) => drop(event, column.key)}
            className={cn(
              "flex min-w-0 flex-col rounded-xl border bg-muted/30 transition-colors lg:h-[calc(100svh-15rem)] lg:min-h-96",
              target && "border-primary/60 bg-primary/5",
            )}
          >
            <h2 className="flex shrink-0 items-center gap-2 border-b px-3 py-2.5 text-sm font-medium">
              <column.icon className={cn("size-4", column.iconClass)} aria-hidden="true" />
              {column.title}
              <span className="ml-auto rounded-full bg-background px-2 py-0.5 text-xs text-muted-foreground tabular-nums">{list.length}</span>
            </h2>
            <div className="min-h-24 flex-1 overflow-y-auto p-2 max-lg:max-h-[60svh]">
              {rows.length === 0 ? (
                <p className="flex h-full min-h-20 items-center justify-center rounded-lg border border-dashed px-3 py-6 text-center text-xs text-muted-foreground">
                  {column.empty}
                </p>
              ) : (
                <ul aria-label={column.title} className="grid gap-2">
                  {rows}
                </ul>
              )}
            </div>
          </section>
        );
      })}
    </div>
  );
}
