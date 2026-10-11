import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { ReplyDraftDialog } from "./ReplyDraftDialog";

const initial = {
  to: [{ name: "Anna Berger", address: "anna@acme.com" }],
  cc: [],
  subject: "Re: Workshop",
  body: "Hallo Anna,\n\nich komme am [Tag].\n\nMax",
};

describe("ReplyDraftDialog", () => {
  it("keeps only once every placeholder is filled in", async () => {
    const onKeep = vi.fn();
    render(<ReplyDraftDialog open onOpenChange={() => {}} initial={initial} onKeep={onKeep} />);

    expect(screen.getByRole("button", { name: "Keep" })).toBeDisabled();
    expect(screen.getByRole("status")).toHaveTextContent("[Tag]");

    const text = screen.getByRole("textbox", { name: "Text" });
    await userEvent.clear(text);
    await userEvent.type(text, "Hallo Anna, ich komme am Freitag. Max");
    await userEvent.click(screen.getByRole("button", { name: "Keep" }));

    expect(onKeep).toHaveBeenCalledWith({ ...initial, body: "Hallo Anna, ich komme am Freitag. Max" });
  });

  it("can keep a draft anyway with placeholders left", async () => {
    const onKeep = vi.fn();
    render(<ReplyDraftDialog open onOpenChange={() => {}} initial={initial} onKeep={onKeep} />);

    await userEvent.click(screen.getByRole("button", { name: "Keep anyway" }));

    expect(onKeep).toHaveBeenCalledWith(initial);
  });

  it("edits recipients and subject", async () => {
    const onKeep = vi.fn();
    render(<ReplyDraftDialog open onOpenChange={() => {}} initial={{ ...initial, body: "Gerne!" }} onKeep={onKeep} />);

    await userEvent.type(screen.getByRole("textbox", { name: "Cc" }), "bob@acme.com{Enter}");
    await userEvent.type(screen.getByRole("textbox", { name: "Cc" }), "not-an-address{Enter}");
    const subject = screen.getByRole("textbox", { name: "Subject" });
    await userEvent.clear(subject);
    await userEvent.type(subject, "Re: Workshop am Freitag");
    await userEvent.click(screen.getByRole("button", { name: "Keep" }));

    expect(onKeep).toHaveBeenCalledWith({ ...initial, body: "Gerne!", cc: [{ address: "bob@acme.com" }], subject: "Re: Workshop am Freitag" });
  });

  it("needs at least one recipient", async () => {
    render(<ReplyDraftDialog open onOpenChange={() => {}} initial={{ ...initial, body: "Gerne!" }} onKeep={() => {}} />);

    await userEvent.click(screen.getByRole("button", { name: "Remove anna@acme.com from To" }));

    expect(screen.getByRole("button", { name: "Keep" })).toBeDisabled();
  });
});
