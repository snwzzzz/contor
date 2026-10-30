import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
import { AiPermissionsForm, permissionsOf, type AiPermissions } from "./AiPermissionsForm";

const initial: AiPermissions = {
  mailAccess: false,
  historyDays: 90,
  categories: "SUGGEST",
  records: "SUGGEST",
  calendar: "SUGGEST",
  tasks: "SUGGEST",
  replies: "OFF",
  externalApis: "SUGGEST",
  courtesyReplies: false,
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
  it("lets the user describe themselves and their business for the AI", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<Harness onChange={onChange} />);
    await user.click(screen.getByRole("checkbox", { name: /read and analyze my mailboxes/i }));

    await user.type(screen.getByRole("textbox", { name: /about you and your business/i }), "Wholesaler");

    expect(onChange).toHaveBeenLastCalledWith({ ...initial, mailAccess: true, ownerProfile: "Wholesaler" });
  });

  it("keeps the owner profile when reading the saved settings", () => {
    // The hidden-mailbox setting is a General setting: not part of the permissions.
    expect(permissionsOf({ ...initial, ownerProfile: "Head of Operations", readHiddenMailboxes: false, onboardedAt: "2026-10-13T08:00:00Z" })).toEqual({
      ...initial,
      ownerProfile: "Head of Operations",
    });
  });

  it("asks for every area with off, ask-me and automatic choices", () => {
    render(<AiPermissionsForm value={initial} onChange={() => {}} />);

    for (const area of ["New categories", "Contacts & records", "Calendar", "To-dos", "External APIs"]) {
      const group = screen.getByRole("radiogroup", { name: area });
      expect(within(group).getAllByRole("radio").map((radio) => radio.getAttribute("value"))).toEqual(["OFF", "SUGGEST", "AUTO"]);
      expect(within(group).getByRole("radio", { name: "Ask me" })).toBeChecked();
    }
  });

  it("offers reply drafts only off or ask-me, never automatic", async () => {
    const onChange = vi.fn();
    render(<Harness onChange={onChange} />);
    await userEvent.click(screen.getByRole("checkbox", { name: /read and analyze my mailboxes/i }));

    const replies = screen.getByRole("radiogroup", { name: "Reply drafts" });
    expect(within(replies).getAllByRole("radio").map((radio) => radio.getAttribute("value"))).toEqual(["OFF", "SUGGEST"]);
    expect(screen.getByText(/drafts are never sent/i)).toBeInTheDocument();
    await userEvent.click(within(replies).getByRole("radio", { name: "Ask me" }));

    expect(onChange).toHaveBeenLastCalledWith({ ...initial, mailAccess: true, replies: "SUGGEST" });
  });

  it("offers polite replies as an extra once reply drafts are on", async () => {
    const onChange = vi.fn();
    render(<Harness onChange={onChange} />);
    await userEvent.click(screen.getByRole("checkbox", { name: /read and analyze my mailboxes/i }));
    const courtesy = screen.getByRole("checkbox", { name: /also suggest polite replies/i });
    expect(courtesy).toBeDisabled();

    await userEvent.click(within(screen.getByRole("radiogroup", { name: "Reply drafts" })).getByRole("radio", { name: "Ask me" }));
    await userEvent.click(courtesy);

    expect(onChange).toHaveBeenLastCalledWith({ ...initial, mailAccess: true, replies: "SUGGEST", courtesyReplies: true });
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

  it("grays out the history window and the profile while mail access is off", () => {
    render(<AiPermissionsForm value={initial} onChange={() => {}} />);

    const history = screen.getByRole("combobox", { name: /how far back/i });
    const profile = screen.getByRole("textbox", { name: /about you/i });
    expect(history).toBeDisabled();
    expect(profile).toBeDisabled();
    // Only the inputs fade; labels and hints stay as they are.
    expect(history).toHaveClass("disabled:opacity-50");
    expect(profile).not.toHaveClass("disabled:opacity-100");
    expect(history.parentElement?.className).not.toMatch(/opacity/);
    expect(profile.parentElement?.className).not.toMatch(/opacity/);
    const calendar = screen.getByRole("radiogroup", { name: "Calendar" });
    expect(calendar).toHaveClass("has-disabled:opacity-50");
    expect(calendar.parentElement?.className).not.toMatch(/opacity/);
  });

  it("keeps each area's choices on the right of its label, however long the description", () => {
    render(<AiPermissionsForm value={initial} onChange={() => {}} />);

    for (const name of ["All areas", "New categories", "Calendar", "To-dos", "Reply drafts"]) {
      const row = screen.getByRole("radiogroup", { name }).parentElement;
      expect(row).toHaveClass("grid", "grid-cols-[minmax(0,1fr)_auto]");
      expect(row).not.toHaveClass("flex-wrap");
    }
  });

  it("keeps each hidden radio inside its own label, so focusing it never scrolls anything else", () => {
    render(<AiPermissionsForm value={initial} onChange={() => {}} />);

    for (const radio of screen.getAllByRole("radio")) {
      expect(radio).toHaveClass("sr-only");
      expect(radio.closest("label")).toHaveClass("relative");
    }
  });

  it("sets every area at once with the preset under the profile", async () => {
    const onChange = vi.fn();
    render(<Harness onChange={onChange} />);
    await userEvent.click(screen.getByRole("checkbox", { name: /read and analyze my mailboxes/i }));
    const all = screen.getByRole("radiogroup", { name: "All areas" });
    // The saved areas are mixed (replies are off), so no preset is checked yet.
    expect(within(all).queryByRole("radio", { checked: true })).not.toBeInTheDocument();

    await userEvent.click(within(all).getByRole("radio", { name: "Automatic" }));

    // Reply drafts are never sent, so they stay at "Ask me".
    expect(onChange).toHaveBeenLastCalledWith({
      ...initial, mailAccess: true, categories: "AUTO", records: "AUTO", calendar: "AUTO", tasks: "AUTO", replies: "SUGGEST", externalApis: "AUTO",
    });
    expect(within(all).getByRole("radio", { name: "Automatic" })).toBeChecked();

    await userEvent.click(within(all).getByRole("radio", { name: "Off" }));
    expect(onChange).toHaveBeenLastCalledWith({
      ...initial, mailAccess: true, categories: "OFF", records: "OFF", calendar: "OFF", tasks: "OFF", replies: "OFF", externalApis: "OFF",
    });
    expect(within(screen.getByRole("radiogroup", { name: "Calendar" })).getByRole("radio", { name: "Off" })).toBeChecked();
  });

  it("shows the preset that matches the areas, and only lets it be used with mail access", () => {
    render(<AiPermissionsForm value={{ ...initial, replies: "SUGGEST" }} onChange={() => {}} />);

    const all = screen.getByRole("radiogroup", { name: "All areas" });
    expect(within(all).getByRole("radio", { name: "Ask me" })).toBeChecked();
    expect(within(all).getByRole("radio", { name: "Automatic" })).toBeDisabled();
  });
});
