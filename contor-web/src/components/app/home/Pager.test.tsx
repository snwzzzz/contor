import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { Pager, usePages } from "./Pager";

function Letters({ items, size }: { items: readonly string[]; size: number }) {
  const pages = usePages(items, size);
  return (
    <div>
      <ul>
        {pages.items.map((item) => (
          <li key={item}>{item}</li>
        ))}
      </ul>
      <Pager label="Letters" {...pages} />
    </div>
  );
}

const texts = () => screen.getAllByRole("listitem").map((item) => item.textContent);

describe("Pager", () => {
  it("turns the pages like a book and stops at both ends", async () => {
    render(<Letters items={["a", "b", "c", "d", "e"]} size={2} />);

    expect(texts()).toEqual(["a", "b"]);
    expect(screen.getByText("1 / 3")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Previous page" })).toBeDisabled();

    await userEvent.click(screen.getByRole("button", { name: "Next page" }));
    await userEvent.click(screen.getByRole("button", { name: "Next page" }));

    expect(texts()).toEqual(["e"]);
    expect(screen.getByText("3 / 3")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Next page" })).toBeDisabled();

    await userEvent.click(screen.getByRole("button", { name: "Previous page" }));
    expect(texts()).toEqual(["c", "d"]);
  });

  it("stays hidden when everything fits on one page", () => {
    render(<Letters items={["a", "b"]} size={2} />);

    expect(screen.queryByRole("navigation", { name: "Letters pages" })).not.toBeInTheDocument();
  });
});
