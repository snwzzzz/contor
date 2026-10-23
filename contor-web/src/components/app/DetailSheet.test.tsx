import { act, fireEvent, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ListTodo } from "lucide-react";
import Link from "next/link";
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

  it("can show a compact bar instead of the header: the trail, then the title, the actions and close in one row", () => {
    render(
      <DetailSheet
        open
        onOpenChange={() => {}}
        title="Vertex Financial"
        trail={<Link href="/collections/3">Customers</Link>}
        actions={<button type="button">Full screen</button>}
      >
        <p>Body</p>
      </DetailSheet>,
    );

    const dialog = screen.getByRole("dialog", { name: "Vertex Financial" });
    const bar = dialog.querySelector("[data-slot=sheet-bar]") as HTMLElement;
    expect(bar).toHaveClass("h-12", "border-b");
    const trail = within(bar).getByRole("navigation", { name: "Breadcrumb" });
    expect(within(trail).getByRole("link", { name: "Customers" })).toHaveAttribute("href", "/collections/3");
    // The title is the last crumb (and still names the dialog).
    expect(within(trail).getByText("Vertex Financial")).toHaveAttribute("aria-current", "page");
    expect(within(bar).getByRole("button", { name: "Full screen" })).toBeInTheDocument();
    expect(within(bar).getByRole("button", { name: "Close" })).toBeInTheDocument();
    expect(dialog.querySelector("[data-slot=sheet-header]")).toBeNull();
  });

  it("shows the icon bare and small next to the label, like the page headers: no colored tile", () => {
    render(
      <DetailSheet open onOpenChange={() => {}} label="New to-do" title="Send certificates" icon={ListTodo} tint={4}>
        <p>Body</p>
      </DetailSheet>,
    );

    const label = screen.getByText("New to-do");
    const icon = label.parentElement?.querySelector("svg");
    expect(icon).not.toBeNull();
    expect(icon).toHaveClass("size-3.5");
    expect(icon?.getAttribute("class")).toMatch(/text-tint-4/);
    // Nothing in the header sits on a tinted background.
    const header = screen.getByRole("dialog", { name: "Send certificates" }).querySelector("[data-slot=sheet-header]");
    expect(header?.querySelector("[class*='bg-tint']")).toBeNull();
    expect(header?.querySelector(".rounded-lg")).toBeNull();
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

  describe("size and backdrop", () => {
    afterEach(() => window.localStorage.clear());

    function open() {
      return render(
        <DetailSheet open onOpenChange={() => {}} title="Details">
          <p>Body</p>
        </DetailSheet>,
      );
    }

    /** The dragged width; only used from sm up (phones stay full width). */
    function sheetWidth() {
      const dialog = screen.getByRole("dialog", { name: "Details" });
      return dialog.className.includes("sm:w-(--sheet-width)") ? dialog.style.getPropertyValue("--sheet-width") : "";
    }

    function drag(handle: HTMLElement, toX: number) {
      fireEvent.pointerDown(handle);
      act(() => {
        window.dispatchEvent(new MouseEvent("pointermove", { clientX: toX }));
        window.dispatchEvent(new MouseEvent("pointerup", { clientX: toX }));
      });
    }

    it("leaves the page behind it as it is: no dimming, no blur", () => {
      open();

      const overlay = document.querySelector("[data-slot=sheet-overlay]");
      expect(overlay?.className).toMatch(/backdrop-blur-none/);
      expect(overlay?.className).toMatch(/bg-transparent/);
      expect(overlay?.className).not.toMatch(/bg-black/);
    });

    it("floats inside the window with a slight border all around and rounded corners (edge to edge on phones)", () => {
      open();

      const dialog = screen.getByRole("dialog", { name: "Details" });
      expect(dialog).toHaveClass(
        "data-[side=right]:sm:inset-y-2",
        "data-[side=right]:sm:right-2",
        "data-[side=right]:sm:h-auto",
        "data-[side=right]:sm:rounded-xl",
        "data-[side=right]:sm:border",
        "sm:overflow-hidden",
      );
    });

    it("opens at a third of the window (at least 24rem), full width on phones", () => {
      open();

      const dialog = screen.getByRole("dialog", { name: "Details" });
      expect(dialog.className).toContain("sm:w-[max(33.333vw,24rem)]");
      expect(dialog.style.getPropertyValue("--sheet-width")).toBe("");
    });

    it("is resized by dragging its left edge, and remembers the width", () => {
      const { unmount } = open();
      const handle = screen.getByRole("separator", { name: "Resize" });
      expect(handle).toHaveAttribute("aria-orientation", "vertical");

      // jsdom's window is 1024px wide: the edge dragged to x = 424 leaves a 600px sheet.
      drag(handle, 424);

      expect(sheetWidth()).toBe("600px");
      expect(handle).toHaveAttribute("aria-valuenow", "600");
      unmount();
      open();
      expect(sheetWidth()).toBe("600px");
    });

    it("never gets narrower than 20rem or wider than 90% of the window", () => {
      open();
      const handle = screen.getByRole("separator", { name: "Resize" });

      drag(handle, 1000);
      expect(sheetWidth()).toBe("320px");
      drag(handle, 0);
      expect(sheetWidth()).toBe(`${Math.round(1024 * 0.9)}px`);
    });

    it("resizes with the arrow keys and goes back to the default on double click", () => {
      open();
      const handle = screen.getByRole("separator", { name: "Resize" });
      drag(handle, 424);

      fireEvent.keyDown(handle, { key: "ArrowLeft" });
      expect(sheetWidth()).toBe("632px");
      fireEvent.keyDown(handle, { key: "ArrowRight" });
      fireEvent.keyDown(handle, { key: "ArrowRight" });
      expect(sheetWidth()).toBe("568px");

      fireEvent.doubleClick(handle);
      expect(sheetWidth()).toBe("");
    });
  });
});
