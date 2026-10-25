import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { TaskBoardSkeleton, TasksSkeleton } from "./TaskBoardSkeleton";

describe("TaskBoardSkeleton", () => {
  it("the board keeps its three named columns with placeholder cards", () => {
    render(<TaskBoardSkeleton />);

    const status = screen.getByRole("status", { name: "Loading to-dos" });
    expect(within(status).getByText("To do")).toBeInTheDocument();
    expect(within(status).getByText("In progress")).toBeInTheDocument();
    expect(within(status).getByText("Done")).toBeInTheDocument();
    expect(status.querySelectorAll("[data-skeleton=card]").length).toBeGreaterThan(3);
    expect(status.querySelector("[data-skeleton=new-task]")).toBeNull();
  });

  it("the whole page also has the field for a new to-do above the board", () => {
    render(<TasksSkeleton />);

    const status = screen.getByRole("status", { name: "Loading to-dos" });
    expect(status.querySelector("[data-skeleton=new-task]")).not.toBeNull();
    expect(status.querySelectorAll("[data-skeleton=column]")).toHaveLength(3);
  });
});
