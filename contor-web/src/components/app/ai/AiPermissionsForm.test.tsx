import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
import { AiPermissionsForm, type AiPermissions } from "./AiPermissionsForm";

const initial: AiPermissions = {
  mailAccess: false,
  historyDays: 90,
  categories: "SUGGEST",
  records: "SUGGEST",
  calendar: "SUGGEST",
  tasks: "SUGGEST",
};

function Harness({ onChange }: { onChange: (value: AiPermissions) => void }) {
  const [value, setValue] = useState(initial);
  return (
    <AiPermissionsForm
      value={value}
      onChange={(next) => {
        setValue(next);
        onChange(next);
      }}
    />
  );
}

describe("AiPermissionsForm", () => {
  it("asks for every area with off, ask-me and automatic choices", () => {
    render(<AiPermissionsForm value={initial} onChange={() => {}} />);

    for (const area of ["New categories", "Contacts & records", "Calendar", "To-dos"]) {
      const group = screen.getByRole("radiogroup", { name: area });
      expect(within(group).getAllByRole("radio").map((radio) => radio.getAttribute("value"))).toEqual(["OFF", "SUGGEST", "AUTO"]);
      expect(within(group).getByRole("radio", { name: "Ask me" })).toBeChecked();
    }
  });

  it("grants mail access and picks the history window", async () => {
    const onChange = vi.fn();
    render(<Harness onChange={onChange} />);

    await userEvent.click(screen.getByRole("checkbox", { name: /read and analyze my mailboxes/i }));
    await userEvent.selectOptions(screen.getByRole("combobox", { name: /how far back/i }), "365");

    expect(onChange).toHaveBeenLastCalledWith({ ...initial, mailAccess: true, historyDays: 365 });
  });

  it("changes one area's level", async () => {
    const onChange = vi.fn();
    render(<Harness onChange={onChange} />);
    await userEvent.click(screen.getByRole("checkbox", { name: /read and analyze my mailboxes/i }));

    await userEvent.click(within(screen.getByRole("radiogroup", { name: "Calendar" })).getByRole("radio", { name: "Automatic" }));
    await userEvent.click(within(screen.getByRole("radiogroup", { name: "To-dos" })).getByRole("radio", { name: "Off" }));

    expect(onChange).toHaveBeenLastCalledWith({ ...initial, mailAccess: true, calendar: "AUTO", tasks: "OFF" });
  });

  it("disables the areas while mail access is off", () => {
    render(<AiPermissionsForm value={initial} onChange={() => {}} />);

    expect(within(screen.getByRole("radiogroup", { name: "Calendar" })).getByRole("radio", { name: "Automatic" })).toBeDisabled();
  });
});
