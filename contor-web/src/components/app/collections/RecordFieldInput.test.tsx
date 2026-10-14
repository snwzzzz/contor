import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import type { CollectionFieldDto } from "@/lib/api/generated/model/collectionFieldDto";
import { RecordFieldInput } from "./RecordFieldInput";

function field(type: CollectionFieldDto["type"], options: CollectionFieldDto["options"] = {}): CollectionFieldDto {
  return { id: 1, slug: "f", name: "Field", type, options, required: false, uniqueValue: false, position: 0 };
}

describe("RecordFieldInput", () => {
  it("edits text and reports the new value", async () => {
    const onChange = vi.fn();
    render(<RecordFieldInput field={field("TEXT")} value="Acme" onChange={onChange} />);

    await userEvent.type(screen.getByRole("textbox", { name: "Field" }), "!");

    expect(onChange).toHaveBeenLastCalledWith("Acme!");
  });

  it("reports an emptied text as null", async () => {
    const onChange = vi.fn();
    render(<RecordFieldInput field={field("TEXT")} value="A" onChange={onChange} />);

    await userEvent.clear(screen.getByRole("textbox", { name: "Field" }));

    expect(onChange).toHaveBeenLastCalledWith(null);
  });

  it("uses a text area for long text", () => {
    render(<RecordFieldInput field={field("LONG_TEXT")} value={"a\nb"} onChange={() => {}} />);

    expect(screen.getByRole("textbox", { name: "Field" }).tagName).toBe("TEXTAREA");
  });

  it("edits a date as ISO text and clears it to null", async () => {
    const onChange = vi.fn();
    render(<RecordFieldInput field={field("DATE")} value="2026-10-08" onChange={onChange} />);

    const input = screen.getByLabelText("Field");
    expect(input).toHaveValue("2026-10-08");
    await userEvent.clear(input);

    expect(onChange).toHaveBeenLastCalledWith(null);
  });

  it("offers the enum values, the status states and an empty choice", async () => {
    const onChange = vi.fn();
    const { rerender } = render(<RecordFieldInput field={field("ENUM", { values: ["low", "high"] })} value="low" onChange={onChange} />);

    const select = screen.getByRole("combobox", { name: "Field" });
    expect([...select.querySelectorAll("option")].map((option) => option.textContent)).toEqual(["–", "Low", "High"]);
    await userEvent.selectOptions(select, "high");
    expect(onChange).toHaveBeenLastCalledWith("high");

    rerender(<RecordFieldInput field={field("STATUS", { states: ["open", "done"] })} value="open" onChange={onChange} />);
    expect([...screen.getByRole("combobox", { name: "Field" }).querySelectorAll("option")].map((option) => option.textContent)).toEqual(["–", "Open", "Done"]);
  });

  it("shows an error and marks the input invalid", () => {
    render(<RecordFieldInput field={field("TEXT")} value="" onChange={() => {}} error="Required" />);

    expect(screen.getByRole("textbox", { name: "Field" })).toBeInvalid();
    expect(screen.getByText("Required")).toBeInTheDocument();
  });

  it("shows types it cannot edit yet as text", () => {
    render(<RecordFieldInput field={field("MONEY")} value={{ amount: 12.5, currency: "EUR" }} onChange={() => {}} />);

    expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
    expect(screen.getByText(/12\.5|12,5/)).toBeInTheDocument();
  });
});
