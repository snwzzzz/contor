import { fireEvent, render, screen, within } from "@testing-library/react";
import { CircleCheck, CircleDashed } from "lucide-react";
import { describe, expect, it, vi } from "vitest";
import { KanbanBoard, type BoardColumn } from "./KanbanBoard";

type Key = "todo" | "done";

interface Card {
  id: number;
  title: string;
}

const COLUMNS: readonly BoardColumn<Key>[] = [
  { key: "todo", title: "To do", icon: CircleDashed, empty: "Nothing to do." },
  { key: "done", title: "Done", icon: CircleCheck, empty: "Nothing done yet." },
];

const dataTransfer = () => ({ setData: vi.fn(), getData: vi.fn(), setDragImage: vi.fn(), dropEffect: "none", effectAllowed: "all" });

/** The rendered board (the drag image is a clone outside it, appended to the body for a moment). */
let rendered: HTMLElement;

function board(items: Record<Key, Card[]>, onMove = vi.fn()) {
  rendered = render(
    <KanbanBoard
      columns={COLUMNS}
      items={items}
      getId={(card) => card.id}
      renderCard={(card) => <span>{card.title}</span>}
      renderPreview={(card, column) => <span>{`${card.title} in ${column}`}</span>}
      onMove={onMove}
    />,
  ).container;
  return onMove;
}

/** Cards stacked 50px apart, 40px tall, in document order. */
function layOut(list: HTMLElement) {
  within(list).getAllByRole("listitem").forEach((row, index) => {
    vi.spyOn(row, "getBoundingClientRect").mockReturnValue({ top: index * 50, height: 40, bottom: index * 50 + 40 } as DOMRect);
  });
}

const card = (title: string) => within(rendered).getByText(title).closest("li") as HTMLElement;

describe("KanbanBoard", () => {
  it("shows the columns with their counts and an empty text", () => {
    board({ todo: [{ id: 1, title: "A" }], done: [] });

    expect(within(screen.getByRole("region", { name: "To do" })).getByText("1")).toBeInTheDocument();
    expect(within(screen.getByRole("region", { name: "Done" })).getByText("Nothing done yet.")).toBeInTheDocument();
    expect(card("A")).toHaveAttribute("draggable", "true");
  });

  it("reorders inside a column at the pointer and previews the slot", () => {
    const onMove = board({ todo: [{ id: 1, title: "A" }, { id: 2, title: "B" }, { id: 3, title: "C" }], done: [] });
    const todo = screen.getByRole("region", { name: "To do" });
    layOut(screen.getByRole("list", { name: "To do" }));
    const transfer = dataTransfer();

    fireEvent.dragStart(card("C"), { dataTransfer: transfer });
    expect(card("C")).toHaveAttribute("data-dragging", "true");
    // Above the middle of B (y 50..90, middle 70): C goes between A and B.
    fireEvent.dragOver(todo, { dataTransfer: transfer, clientY: 60 });

    const rows = within(screen.getByRole("list", { name: "To do" })).getAllByRole("listitem", { hidden: true }).filter((row) => !row.hidden);
    expect(rows.map((row) => row.textContent)).toEqual(["A", "C in todo", "B"]);
    fireEvent.drop(todo, { dataTransfer: transfer, clientY: 60 });

    expect(onMove).toHaveBeenCalledWith({ id: 3, title: "C" }, "todo", 1);
    expect(screen.queryByTestId("drop-preview")).not.toBeInTheDocument();
  });

  it("does nothing when a card is dropped where it was", () => {
    const onMove = board({ todo: [{ id: 1, title: "A" }, { id: 2, title: "B" }], done: [] });
    const todo = screen.getByRole("region", { name: "To do" });
    layOut(screen.getByRole("list", { name: "To do" }));
    const transfer = dataTransfer();

    fireEvent.dragStart(card("A"), { dataTransfer: transfer });
    fireEvent.dragOver(todo, { dataTransfer: transfer, clientY: 10 });
    fireEvent.drop(todo, { dataTransfer: transfer, clientY: 10 });

    expect(onMove).not.toHaveBeenCalled();
  });

  it("moves a card into another column at the pointer", () => {
    const onMove = board({ todo: [{ id: 1, title: "A" }], done: [{ id: 2, title: "B" }, { id: 3, title: "C" }] });
    const done = screen.getByRole("region", { name: "Done" });
    layOut(screen.getByRole("list", { name: "Done" }));
    const transfer = dataTransfer();

    fireEvent.dragStart(card("A"), { dataTransfer: transfer });
    fireEvent.dragOver(done, { dataTransfer: transfer, clientY: 200 });
    expect(done).toHaveAttribute("data-drop-target", "true");
    expect(within(done).getByTestId("drop-preview")).toHaveTextContent("A in done");
    fireEvent.drop(done, { dataTransfer: transfer, clientY: 200 });

    expect(onMove).toHaveBeenCalledWith({ id: 1, title: "A" }, "done", 2);
  });

  it("previews a drop into an empty column instead of its empty text and clears everything when the drag ends", () => {
    board({ todo: [{ id: 1, title: "A" }], done: [] });
    const done = screen.getByRole("region", { name: "Done" });
    const transfer = dataTransfer();

    fireEvent.dragStart(card("A"), { dataTransfer: transfer });
    fireEvent.dragOver(done, { dataTransfer: transfer, clientY: 5 });
    expect(within(done).getByTestId("drop-preview")).toBeInTheDocument();
    expect(within(done).queryByText("Nothing done yet.")).not.toBeInTheDocument();

    fireEvent.dragEnd(card("A"), { dataTransfer: transfer });
    expect(screen.queryByTestId("drop-preview")).not.toBeInTheDocument();
    expect(card("A")).not.toHaveAttribute("data-dragging");
  });
});
