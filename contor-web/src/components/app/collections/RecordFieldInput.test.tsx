import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
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

  it("edits a number and reports it as a number", async () => {
    const onChange = vi.fn();
    render(<RecordFieldInput field={field("NUMBER")} value={4} onChange={onChange} />);

    await userEvent.type(screen.getByRole("spinbutton", { name: "Field" }), "2");

    expect(onChange).toHaveBeenLastCalledWith(42);
  });

  it("edits money as an amount and a currency", async () => {
    const onChange = vi.fn();
    function Harness() {
      const [value, setValue] = useState<unknown>({ amount: 10, currency: "EUR" });
      return <RecordFieldInput field={field("MONEY")} value={value} onChange={(next) => { setValue(next); onChange(next); }} />;
    }
    render(<Harness />);

    await userEvent.type(screen.getByRole("spinbutton", { name: "Field" }), "0");
    expect(onChange).toHaveBeenLastCalledWith({ amount: 100, currency: "EUR" });

    await userEvent.clear(screen.getByRole("textbox", { name: "Field currency" }));
    await userEvent.type(screen.getByRole("textbox", { name: "Field currency" }), "usd");
    expect(onChange).toHaveBeenLastCalledWith({ amount: 100, currency: "USD" });
  });

  it("edits yes/no with an empty choice", async () => {
    const onChange = vi.fn();
    render(<RecordFieldInput field={field("BOOLEAN")} value={true} onChange={onChange} />);

    await userEvent.selectOptions(screen.getByRole("combobox", { name: "Field" }), "No");
    expect(onChange).toHaveBeenLastCalledWith(false);
    await userEvent.selectOptions(screen.getByRole("combobox", { name: "Field" }), "–");
    expect(onChange).toHaveBeenLastCalledWith(null);
  });

  it("edits a date and time and reports it as an ISO instant", () => {
    render(<RecordFieldInput field={field("DATETIME")} value="2026-10-19T08:30:00Z" onChange={vi.fn()} />);

    const input = screen.getByLabelText("Field");
    expect(input).toHaveAttribute("type", "datetime-local");
    expect((input as HTMLInputElement).value).toMatch(/^2026-10-19T\d{2}:30$/);
  });

  it("edits a related record by its id, and a file by its name", async () => {
    const onRelation = vi.fn();
    const { unmount } = render(<RecordFieldInput field={field("RELATION")} value={7} onChange={onRelation} />);
    await userEvent.type(screen.getByRole("spinbutton", { name: "Field" }), "1");
    expect(onRelation).toHaveBeenLastCalledWith(71);
    unmount();

    const onFile = vi.fn();
    render(<RecordFieldInput field={field("FILE")} value="a.pdf" onChange={onFile} />);
    await userEvent.type(screen.getByRole("textbox", { name: "Field" }), "x");
    expect(onFile).toHaveBeenLastCalledWith("a.pdfx");
  });

  it("offers to open an email address, phone number or link next to its input", () => {
    render(<RecordFieldInput field={field("EMAIL")} value="anna@acme.com" onChange={vi.fn()} />);

    expect(screen.getByRole("link", { name: "Send email to anna@acme.com" })).toHaveAttribute("href", "mailto:anna@acme.com");
  });

  it("uses the shared field look for choices too", () => {
    render(<RecordFieldInput field={field("ENUM", { values: ["a", "b"] })} value="a" onChange={vi.fn()} />);

    expect(screen.getByRole("combobox", { name: "Field" })).toHaveClass("h-8", "rounded-md");
  });
});
