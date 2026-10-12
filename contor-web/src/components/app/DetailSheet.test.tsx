import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { DetailSheet } from "./DetailSheet";

describe("DetailSheet", () => {
  it("shows label, title, body and footer", () => {
    render(
      <DetailSheet open onOpenChange={() => {}} label="New to-do" title="Send certificates" footer={<button>Approve</button>}>
        <p>Body text</p>
      </DetailSheet>,
    );

    const dialog = screen.getByRole("dialog", { name: "Send certificates" });
    expect(dialog).toHaveTextContent("New to-do");
    expect(dialog).toHaveTextContent("Body text");
    expect(screen.getByRole("button", { name: "Approve" })).toBeInTheDocument();
  });

  it("asks to close on Escape and on the close button", async () => {
    const onOpenChange = vi.fn();
    render(
      <DetailSheet open onOpenChange={onOpenChange} title="Details">
        <p>Body</p>
      </DetailSheet>,
    );

    await userEvent.keyboard("{Escape}");
    expect(onOpenChange).toHaveBeenCalledWith(false);
    onOpenChange.mockClear();
    await userEvent.click(screen.getByRole("button", { name: "Close" }));
    expect(onOpenChange).toHaveBeenCalledWith(false);
  });

  it("renders nothing while closed", () => {
    render(<DetailSheet open={false} onOpenChange={() => {}} title="Details"><p>Body</p></DetailSheet>);

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });
});
