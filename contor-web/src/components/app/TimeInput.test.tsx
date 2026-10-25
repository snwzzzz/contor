import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { setTimeFormat } from "@/lib/time-format";
import { TimeInput } from "./TimeInput";

function Harness({ initial, onChange }: { initial: string; onChange?: (value: string) => void }) {
  const [value, setValue] = useState(initial);
  return (
    <>
      <TimeInput
        aria-label="Time"
        value={value}
        onChange={(next) => {
          setValue(next);
          onChange?.(next);
        }}
      />
      <output aria-label="Value">{value}</output>
    </>
  );
}

describe("TimeInput", () => {
  afterEach(() => window.localStorage.clear());

  it("shows the time as 24-hour time by default", () => {
    render(<Harness initial="14:00" />);

    expect(screen.getByRole("textbox", { name: "Time" })).toHaveValue("14:00");
  });

  it("shows the time with AM/PM when that format is chosen, also after switching", () => {
    render(<Harness initial="14:00" />);

    act(() => setTimeFormat("12h"));

    expect(screen.getByRole("textbox", { name: "Time" })).toHaveValue("2:00 PM");
  });

  it("takes a typed time in either format and shows it in the chosen one when left", async () => {
    const onChange = vi.fn();
    render(<Harness initial="" onChange={onChange} />);
    const input = screen.getByRole("textbox", { name: "Time" });

    await userEvent.type(input, "9:30 pm");
    expect(screen.getByRole("status", { hidden: true, name: "Value" })).toHaveTextContent("21:30");
    expect(input).toHaveValue("9:30 pm");

    await userEvent.tab();
    expect(input).toHaveValue("21:30");
    expect(onChange).toHaveBeenLastCalledWith("21:30");
  });

  it("keeps the last valid time when the typed text is not one", async () => {
    render(<Harness initial="08:00" />);
    const input = screen.getByRole("textbox", { name: "Time" });

    await userEvent.tripleClick(input);
    await userEvent.keyboard("later");
    await userEvent.tab();

    expect(input).toHaveValue("08:00");
  });

  it("clears the time when the field is emptied", async () => {
    render(<Harness initial="08:00" />);
    const input = screen.getByRole("textbox", { name: "Time" });

    await userEvent.clear(input);
    await userEvent.tab();

    expect(input).toHaveValue("");
    expect(screen.getByRole("status", { hidden: true, name: "Value" })).toBeEmptyDOMElement();
  });
});
